package com.mirage.console.ui;

import com.mirage.common.trace.TraceBacklog;
import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;
import com.mirage.console.ShellFilters;
import com.mirage.console.ThreatLevel;
import com.mirage.ui.shell.ShellTheme;
import com.mirage.ui.shell.ShellWindow;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * One window per attacker source. A big banner escalates as the source does
 * ({@code PROBE DETECTED} → {@code UNDER ATTACK} → {@code COMPROMISED}, then
 * {@code NEUTRALISED} once a Sensor confirms a block), above a live shell of the
 * Console's own pipeline for that source: receive thread → engine → rules →
 * ledger → response → ACK. The operator can respond right here; each button runs
 * through the same {@link OperationRunner} as the dashboard (ScopeGuard first).
 *
 * <p>Swing-thread only. Closing just hides it; a further escalation brings it back.
 */
public final class ThreatPopup extends JFrame {

    private static final Operation[] ACTIONS = {
            Operation.TARPIT, Operation.FEED_FAKE, Operation.BLOCK, Operation.COUNTER, Operation.COUNTER_ALL};

    private final String sourceIp;
    private final ShellWindow shell;
    private final JPanel bannerPanel = new JPanel(new GridLayout(2, 1, 0, 2));
    private final JLabel banner = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel why = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel result = new JLabel(" ");
    private ThreatLevel level;

    /**
     * @param sourceIp the attacker source this popup follows
     * @param backlog  the Console's trace fan-out; the shell replays what it missed, then goes live
     * @param runner   how operator buttons run (opens an operation shell in the live dashboard)
     * @param inspect  selects the source in the dashboard
     */
    public ThreatPopup(String sourceIp, TraceBacklog backlog, OperationRunner runner, Consumer<String> inspect) {
        super("Mirage — threat from " + sourceIp);
        this.sourceIp = sourceIp;
        this.shell = new ShellWindow(ShellTheme.console(), ShellFilters.threat(sourceIp));
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setLayout(new BorderLayout());

        banner.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        banner.setOpaque(false);
        why.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        why.setOpaque(false);
        bannerPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        bannerPanel.add(banner);
        bannerPanel.add(why);
        add(bannerPanel, BorderLayout.NORTH);

        add(shell, BorderLayout.CENTER);
        add(buildActions(runner, inspect), BorderLayout.SOUTH);

        setSize(920, 600);
        backlog.attach(shell::onTrace, true); // the lines that led to this popup, then live ones
    }

    private JPanel buildActions(OperationRunner runner, Consumer<String> inspect) {
        JPanel south = new JPanel(new BorderLayout(6, 2));
        south.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        for (Operation op : ACTIONS) {
            JButton b = new JButton(op.label());
            b.addActionListener(e -> {
                result.setText("running " + op.label() + " → " + sourceIp + " …");
                runner.run(op, sourceIp, result::setText);
            });
            buttons.add(b);
        }
        JButton show = new JButton("Inspect in dashboard");
        show.addActionListener(e -> inspect.accept(sourceIp));
        buttons.add(show);
        south.add(buttons, BorderLayout.NORTH);
        south.add(result, BorderLayout.SOUTH);
        return south;
    }

    /** The banner line marking an escalation in a shell's timeline. */
    public static TraceEvent bannerLine(String sourceIp, ThreatLevel level) {
        return new TraceEvent(Instant.now(), "console", "threat-monitor", "threat",
                sourceIp, TraceLevel.BANNER, level.banner());
    }

    /**
     * Move the banner to {@code next}, note why, and bring the window forward.
     *
     * @param shellBanner also write the banner into this popup's shell (when it is
     *                    not already in the shared timeline)
     */
    public void escalate(ThreatLevel next, String reason, boolean autoResponseOn, boolean shellBanner) {
        this.level = next;
        banner.setText(next.banner() + "   ·   " + sourceIp);
        Color bg = background(next);
        bannerPanel.setBackground(bg);
        Color fg = next == ThreatLevel.PROBE_DETECTED ? Color.BLACK : Color.WHITE;
        banner.setForeground(fg);
        why.setForeground(fg);
        String tail = next == ThreatLevel.NEUTRALISED
                ? "Sensor confirmed the block — the source is cut off"
                : autoResponseOn ? "auto-response ON — the Console is responding by itself"
                : "auto-response OFF — waiting for the operator";
        why.setText((reason == null || reason.isBlank() ? "" : reason + "   ·   ") + tail);
        if (shellBanner) {
            shell.onTrace(bannerLine(sourceIp, next)); // this shell only, not the bus
        }
        if (!isVisible()) {
            setVisible(true);
        }
        toFront();
    }

    public ThreatLevel level() {
        return level;
    }

    public String sourceIp() {
        return sourceIp;
    }

    private static Color background(ThreatLevel level) {
        return switch (level) {
            case PROBE_DETECTED -> new Color(0xF2, 0xC4, 0x4C);
            case UNDER_ATTACK -> new Color(0xE6, 0x6A, 0x1F);
            case COMPROMISED -> new Color(0xC6, 0x28, 0x28);
            case NEUTRALISED -> new Color(0x2E, 0x7D, 0x32);
        };
    }
}
