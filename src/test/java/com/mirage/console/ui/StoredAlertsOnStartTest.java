package com.mirage.console.ui;

import com.mirage.common.Alert;
import com.mirage.common.Classification;
import com.mirage.common.LedgerEntry;
import com.mirage.common.Severity;
import com.mirage.console.AlertLedger;
import com.mirage.console.LedgerSigner;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * FB-02: after a restart the dashboard opened with an empty feed and {@code Ledger: —}, although the
 * ledger had loaded the stored alerts. So a stored alert edited between runs made Verify say
 * BROKEN, with no row to turn red. Now the stored alerts are in the feed and on the map, and the
 * chain is checked as the dashboard opens: a tampered alert is red from the start.
 */
class StoredAlertsOnStartTest {

    private static Alert alert(String ip, String label, String technique, Severity severity, int minute) {
        return new Alert(ip, new Classification(label, technique, severity),
                Instant.parse("2026-11-08T10:00:00Z").plusSeconds(60L * minute));
    }

    /** Three alerts written in an earlier run, as the database hands them back. */
    private static List<LedgerEntry> storedChain() {
        AlertLedger earlier = new AlertLedger(LedgerSigner.NONE);
        earlier.append(alert("10.10.0.7", "Port / service scan", "T1046", Severity.MEDIUM, 0));
        earlier.append(alert("10.10.0.7", "Credential brute-force", "T1110", Severity.HIGH, 1));
        earlier.append(alert("10.10.0.9", "Stolen credentials used", "T1078", Severity.CRITICAL, 2));
        return earlier.entries();
    }

    /** The same chain with entry 1's severity lowered in the database while the Console was down. */
    private static List<LedgerEntry> tamperedChain() {
        List<LedgerEntry> chain = new ArrayList<>(storedChain());
        LedgerEntry e = chain.get(1);
        Alert edited = new Alert(e.alert().sourceIp(), new Classification(e.alert().classification().label(),
                e.alert().classification().attackTechnique(), Severity.LOW), e.alert().timestamp());
        chain.set(1, new LedgerEntry(e.prevHash(), e.hash(), edited, e.signature()));
        return chain;
    }

    @Test
    void theLedgerKnowsWhichAlertsWereLoadedFromStorage() {
        List<LedgerEntry> stored = storedChain();
        AlertLedger ledger = new AlertLedger(LedgerSigner.NONE, stored);
        ledger.append(alert("10.10.0.5", "Port / service scan", "T1046", Severity.MEDIUM, 9)); // this run
        assertEquals(stored.stream().map(LedgerEntry::alert).toList(), ledger.storedAlerts(),
                "only the alerts loaded at start-up, not the ones raised since");
        assertEquals(4, ledger.size());
        assertTrue(new AlertLedger(LedgerSigner.NONE).storedAlerts().isEmpty(), "a new ledger stored nothing");
    }

    @Test
    void storedAlertsAreInTheFeedAndOnTheMapAndTheChainIsCheckedAtStart() throws Exception {
        Shown shown = open(new AlertLedger(LedgerSigner.NONE, storedChain()));
        assertEquals(3, shown.rows, "the three stored alerts are in the feed");
        assertEquals(List.of(), shown.redRows, "an intact chain marks nothing");
        assertEquals(List.of("10.10.0.7", "10.10.0.9"), shown.mapSources.stream().sorted().toList(),
                "their sources are on the map");
        assertTrue(shown.status.contains("Alerts: 3"), shown.status);
        assertTrue(shown.status.contains("Ledger: verified"), "checked at start: " + shown.status);
    }

    @Test
    void anAlertTamperedWithBetweenRunsIsRedAsTheDashboardOpens() throws Exception {
        Shown shown = open(new AlertLedger(LedgerSigner.NONE, tamperedChain()));
        assertEquals(3, shown.rows);
        assertEquals(List.of(1), shown.redRows, "the edited alert's row is red");
        assertTrue(shown.status.contains("Ledger: BROKEN (entry 1)"), shown.status);
    }

    private record Shown(int rows, List<Integer> redRows, List<String> mapSources, String status) { }

    private static Shown open(AlertLedger ledger) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        AtomicReference<Shown> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Dashboard dash = new Dashboard(ledger, () -> 0);
            try {
                Container root = dash.getRootPane();
                AlertTableModel model = (AlertTableModel) ((JTable) find(root, c -> c instanceof JTable)).getModel();
                List<Integer> red = new ArrayList<>();
                for (int row = 0; row < model.getRowCount(); row++) {
                    if (model.isBrokenRow(row)) {
                        red.add(row);
                    }
                }
                NetworkMapPanel map = (NetworkMapPanel) find(root, c -> c instanceof NetworkMapPanel);
                StringBuilder status = new StringBuilder();
                collectLabels(root, status);
                result.set(new Shown(model.getRowCount(), red,
                        map.nodes().stream().map(NetworkMapPanel.Node::sourceIp).toList(), status.toString()));
            } finally {
                dash.dispose();
            }
        });
        return result.get();
    }

    private static Component find(Container root, Predicate<Component> match) {
        for (Component c : root.getComponents()) {
            if (match.test(c)) {
                return c;
            }
            if (c instanceof Container k) {
                Component found = find(k, match);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void collectLabels(Container root, StringBuilder out) {
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel l && l.getText() != null) {
                out.append(l.getText()).append(' ');
            }
            if (c instanceof Container k) {
                collectLabels(k, out);
            }
        }
    }
}
