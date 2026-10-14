package com.mirage.console.ui;

import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerSnapshot;
import com.mirage.common.CommandType;
import com.mirage.console.AttackerDirectory;
import com.mirage.console.AttackerDossier;
import com.mirage.console.ResponseController;

import javax.swing.BorderFactory;
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
import java.util.function.Function;

/**
 * The right-hand detail view for the selected source: an attacker dossier, a
 * replay of exactly what the intruder sent at each decoy, and a manual
 * active-response panel (the override). All three update together.
 */
public final class AttackerDetailPanel extends JPanel {

    private final transient AttackerDirectory directory;
    private final transient ResponseController responses;
    private final transient Function<String, String> counterFn;

    private final JTextArea dossierArea = readOnlyArea();
    private final JTextArea replayArea = readOnlyArea();
    private final JTextArea responseLog = readOnlyArea();
    private final JLabel targetLabel = new JLabel("Target: (none selected)");
    private final List<JButton> commandButtons;

    private String currentSource;

    public AttackerDetailPanel(AttackerDirectory directory, ResponseController responses,
                               Function<String, String> counterFn) {
        super(new BorderLayout());
        this.directory = directory;
        this.responses = responses;
        this.counterFn = counterFn;
        dossierArea.setLineWrap(true);
        dossierArea.setWrapStyleWord(true);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Attacker dossier", new JScrollPane(dossierArea));
        tabs.addTab("Session replay", new JScrollPane(replayArea));
        tabs.addTab("Responses", buildResponsesTab());
        add(tabs, BorderLayout.CENTER);

        commandButtons = collectButtons();
        showSource(null);
    }

    private JPanel buildResponsesTab() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        targetLabel.setFont(targetLabel.getFont().deriveFont(Font.BOLD));
        targetLabel.setAlignmentX(LEFT_ALIGNMENT);
        top.add(targetLabel);

        JPanel defend = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        defend.setAlignmentX(LEFT_ALIGNMENT);
        defend.add(commandButton("Tarpit", CommandType.TARPIT));
        defend.add(commandButton("Feed fake data", CommandType.FEED_FAKE));
        defend.add(commandButton("Block", CommandType.BLOCK));
        defend.add(commandButton("Unblock", CommandType.UNBLOCK));
        top.add(defend);

        JPanel counter = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        counter.setAlignmentX(LEFT_ALIGNMENT);
        counter.add(counterButton());
        counter.add(new JLabel("mirror the attacker's own technique back (Specter, lab range only)"));
        top.add(counter);

        JLabel note = new JLabel("Every command — defensive or counter — is checked by ScopeGuard before it leaves.");
        note.setAlignmentX(LEFT_ALIGNMENT);
        top.add(note);

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(responseLog), BorderLayout.CENTER);
        return panel;
    }

    private JButton commandButton(String text, CommandType type) {
        JButton b = new JButton(text);
        b.addActionListener(e -> {
            if (currentSource != null) {
                responseLog.append(responses.issue(type, currentSource) + "\n");
            }
        });
        return b;
    }

    private JButton counterButton() {
        JButton b = new JButton("Counter-attack");
        b.addActionListener(e -> {
            if (currentSource != null && counterFn != null) {
                responseLog.append(counterFn.apply(currentSource) + "\n");
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
        dossierArea.setText(renderDossier(AttackerDossier.from(snapshot)));
        dossierArea.setCaretPosition(0);
        replayArea.setText(renderReplay(snapshot));
        replayArea.setCaretPosition(0);
    }

    private static String renderDossier(AttackerDossier d) {
        StringBuilder sb = new StringBuilder();
        line(sb, "Source IP", d.sourceIp());
        line(sb, "Active", d.firstSeen() + "  →  " + d.lastSeen());
        line(sb, "Events observed", String.valueOf(d.eventCount()));
        line(sb, "Services probed", joinOrDash(d.servicesProbed()));
        line(sb, "Port probes", String.valueOf(d.portProbes()));
        line(sb, "Credentials tried", d.credentialsTried().isEmpty() ? "—"
                : d.credentialsTried().size() + "   " + joinOrDash(d.credentialsTried()));
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
