import com.mirage.common.Alert;
import com.mirage.common.Classification;
import com.mirage.common.LedgerEntry;
import com.mirage.common.Severity;
import com.mirage.console.AlertBus;
import com.mirage.console.AlertLedger;
import com.mirage.console.KeyPairSigner;
import com.mirage.console.ui.Dashboard;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Builds the real Dashboard, fills it with sample alerts, optionally tampers the
 *  ledger, then clicks the real "Verify ledger" button so the status strip shows
 *  the integrity result. arg[0] = "verified" (default) or "broken". */
public class DashboardShot {
    public static void main(String[] args) throws Exception {
        boolean broken = args.length > 0 && args[0].equals("broken");

        AlertLedger ledger = new AlertLedger(new KeyPairSigner());
        AlertBus bus = new AlertBus();
        Dashboard dash = new Dashboard(ledger, () -> 2);
        bus.subscribe(dash::onAlert);
        SwingUtilities.invokeAndWait(() -> dash.setVisible(true));

        // publish oldest -> newest so the most severe end up on top
        String[][] rows = {
            {"10.10.0.5", "Port / service scan", "T1046", "MEDIUM"},
            {"10.10.0.5", "Password spraying", "T1110.003", "HIGH"},
            {"10.10.0.5", "Credential brute-force", "T1110", "HIGH"},
            {"10.10.0.9", "Multi-host activity (lateral movement)", "T1021", "HIGH"},
            {"10.10.0.7", "Bait file tampered (possible ransomware)", "T1485", "CRITICAL"},
            {"10.10.0.7", "Stolen credentials used (honeytoken)", "T1078", "CRITICAL"},
        };
        for (String[] r : rows) {
            Alert alert = new Alert(r[0], new Classification(r[1], r[2], Severity.valueOf(r[3])), Instant.now());
            ledger.append(alert);
            bus.publish(alert);
            Thread.sleep(120);
        }

        if (broken) {
            tamperOneEntry(ledger);   // alter a stored alert, keep its old hash
        }

        Thread.sleep(600);
        clickVerify(dash);            // exercise the real Verify-ledger button
        Thread.sleep(600);
        Thread.sleep(120000);
    }

    /** Replace one chained entry's alert with a different one while keeping the
     *  recorded hash/signature, so verify() recomputes a mismatching hash. */
    @SuppressWarnings("unchecked")
    private static void tamperOneEntry(AlertLedger ledger) throws Exception {
        Field f = AlertLedger.class.getDeclaredField("chain");
        f.setAccessible(true);
        List<LedgerEntry> chain = (List<LedgerEntry>) f.get(ledger);
        int i = 2;
        LedgerEntry orig = chain.get(i);
        Alert forged = new Alert("10.10.0.5",
                new Classification("Credential brute-force", "T1110", Severity.LOW), // severity quietly downgraded
                orig.alert().timestamp());
        chain.set(i, new LedgerEntry(orig.prevHash(), orig.hash(), forged, orig.signature()));
    }

    private static void clickVerify(Container root) {
        SwingUtilities.invokeLater(() -> {
            AbstractButton b = findButton(root, "Verify ledger");
            if (b != null) {
                b.doClick();
            }
        });
    }

    private static AbstractButton findButton(Container c, String text) {
        for (Component child : c.getComponents()) {
            if (child instanceof JButton btn && text.equals(btn.getText())) {
                return btn;
            }
            if (child instanceof Container inner) {
                AbstractButton found = findButton(inner, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
