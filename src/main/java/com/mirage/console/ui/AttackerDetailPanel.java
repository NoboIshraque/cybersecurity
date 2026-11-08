package com.mirage.console.ui;

import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerSnapshot;
import com.mirage.console.AttackerDirectory;
import com.mirage.console.AttackerDossier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;

/**
 * The right-hand detail view for the selected source: an attacker dossier, a
 * replay of exactly what the intruder sent at each decoy, and a manual
 * active-response panel (the override). All three update together. Every
 * button goes through an {@link OperationRunner}, which in the live dashboard
 * opens an operation shell showing the threads doing the work.
 */
public final class AttackerDetailPanel extends JPanel {

    private final transient AttackerDirectory directory;
    private final transient OperationRunner runner;

    private final JTextArea dossierArea = readOnlyArea();
    private final JTextArea replayArea = readOnlyArea();
    private final JTextArea responseLog = readOnlyArea();
    private final JLabel targetLabel = new JLabel("Target: (none selected)");
    private final List<JButton> commandButtons;

    private String currentSource;
    /** Event total of the snapshot on screen, so refresh() re-renders only on new activity. */
    private long shownEvents = -1;

    public AttackerDetailPanel(AttackerDirectory directory, OperationRunner runner) {
        super(new BorderLayout());
        this.directory = directory;
        this.runner = runner;
        dossierArea.setLineWrap(true);
        dossierArea.setWrapStyleWord(true);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Attacker dossier", new JScrollPane(dossierArea));
        tabs.addTab("Session replay", new JScrollPane(replayArea));
        tabs.addTab("Responses", buildResponsesTab());
        add(tabs, BorderLayout.CENTER);

        commandButtons = collectButtons();
        showSource(null);
        new javax.swing.Timer(1000, e -> refresh()).start(); // B-18: follow the selected source live
    }

    private JPanel buildResponsesTab() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        targetLabel.setFont(targetLabel.getFont().deriveFont(Font.BOLD));
        targetLabel.setAlignmentX(LEFT_ALIGNMENT);
        top.add(targetLabel);

        // FB-04: the rows wrap and the notes wrap, so a narrow window keeps every button and
        // every word (they used to set a ~760 px minimum width and fall off the edge).
        JPanel defend = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
        defend.setAlignmentX(LEFT_ALIGNMENT);
        defend.add(operationButton(Operation.TARPIT));
        defend.add(operationButton(Operation.FEED_FAKE));
        defend.add(operationButton(Operation.BLOCK));
        defend.add(operationButton(Operation.UNBLOCK));
        top.add(defend);

        JPanel counter = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
        counter.setAlignmentX(LEFT_ALIGNMENT);
        counter.add(operationButton(Operation.COUNTER));
        counter.add(operationButton(Operation.COUNTER_ALL));
        top.add(counter);

        top.add(new WrappingNote("turn the attacker's own technique(s) back on them — Specter, lab range only"));
        top.add(Box.createVerticalStrut(4));
        top.add(new WrappingNote("Every command — defensive or counter — is checked by ScopeGuard before it leaves."));

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(responseLog), BorderLayout.CENTER);
        return panel;
    }

    private JButton operationButton(Operation operation) {
        JButton b = new JButton(operation.label());
        b.addActionListener(e -> {
            if (currentSource != null) {
                runner.run(operation, currentSource, line -> responseLog.append(line + "\n"));
            }
        });
        return b;
    }

    /** Switch the detail view to a source (or clear it when {@code sourceIp} is null). */
    public void showSource(String sourceIp) {
        this.currentSource = sourceIp;
        AttackerSnapshot snapshot = sourceIp == null ? null : directory.snapshot(sourceIp);
        targetLabel.setText("Target: " + (sourceIp == null ? "(none selected)" : sourceIp));
        setButtonsEnabled(sourceIp != null);
        if (snapshot == null) {
            dossierArea.setText(sourceIp == null ? "Select an alert or a map node to inspect a source."
                    : "No activity recorded for " + sourceIp + " yet.");
            replayArea.setText("");
            return;
        }
        render(snapshot);
    }

    /** Credentials listed in full; beyond this only the total and the most recent are shown. */
    static final int RECENT_CREDENTIALS = 10;

    /**
     * Re-render the selected source if it has new activity (B-18: the dossier used to be drawn
     * once, when the source was clicked). Runs on a 1 s timer; call on the EDT.
     */
    public void refresh() {
        if (currentSource == null) {
            return;
        }
        AttackerSnapshot snapshot = directory.snapshot(currentSource);
        if (snapshot != null && snapshot.totalEvents() != shownEvents) {
            render(snapshot);
        }
    }

    private void render(AttackerSnapshot snapshot) {
        shownEvents = snapshot.totalEvents();
        dossierArea.setText(renderDossier(AttackerDossier.from(snapshot)));
        dossierArea.setCaretPosition(0);
        replayArea.setText(renderReplay(snapshot));
        replayArea.setCaretPosition(0);
    }

    String dossierText() {
        return dossierArea.getText();
    }

    static String renderDossier(AttackerDossier d) {
        StringBuilder sb = new StringBuilder();
        line(sb, "Source IP", d.sourceIp());
        line(sb, "Active", d.firstSeen() + "  →  " + d.lastSeen());
        line(sb, "Events observed", String.valueOf(d.eventCount()));
        line(sb, "Services probed", joinOrDash(d.servicesProbed()));
        line(sb, "Port probes", String.valueOf(d.portProbes()));
        int tried = d.credentialsTried().size();
        line(sb, "Credentials tried", tried == 0 ? "—"
                : tried <= RECENT_CREDENTIALS ? tried + "   " + joinOrDash(d.credentialsTried())
                : tried + "   (last " + RECENT_CREDENTIALS + ": "
                        + joinOrDash(d.recentCredentials(RECENT_CREDENTIALS)) + ")");
        line(sb, "Passwords tried", joinOrDash(d.passwordsTried()));
        line(sb, "Honeytokens used", joinOrDash(d.honeytokensUsed()));
        line(sb, "Bait files", joinOrDash(d.baitFilesTouched()));
        return sb.toString();
    }

    private static String renderReplay(AttackerSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        for (AttackEvent e : snapshot.events()) {
            sb.append('[').append(e.timestamp()).append("]  ")
              .append(pad(e.decoyType() == null ? "-" : e.decoyType(), 12))
              .append(pad(e.action().name(), 15))
              .append(e.payload() == null ? "" : e.payload())
              .append('\n');
        }
        if (sb.length() == 0) {
            sb.append("(no interactions captured)");
        }
        return sb.toString();
    }

    private static void line(StringBuilder sb, String label, String value) {
        sb.append(pad(label + ":", 20)).append(value).append('\n');
    }

    private static String joinOrDash(List<String> values) {
        return values.isEmpty() ? "—" : String.join(", ", values);
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s + " " : s + " ".repeat(width - s.length());
    }

    private void setButtonsEnabled(boolean enabled) {
        if (commandButtons != null) {
            commandButtons.forEach(b -> b.setEnabled(enabled));
        }
    }

    private List<JButton> collectButtons() {
        List<JButton> found = new java.util.ArrayList<>();
        collectButtons(this, found);
        return found;
    }

    private static void collectButtons(java.awt.Container c, List<JButton> out) {
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof JButton b) {
                out.add(b);
            } else if (child instanceof java.awt.Container inner) {
                collectButtons(inner, out);
            }
        }
    }

    private static JTextArea readOnlyArea() {
        JTextArea area = new JTextArea(10, 48);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return area;
    }
}
