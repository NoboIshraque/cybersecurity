package com.mirage.specter;

import com.mirage.common.LabConfig;
import com.mirage.common.ScopeGuard;
import com.mirage.common.trace.TraceBus;
import com.mirage.common.trace.TraceLevel;
import com.mirage.ui.shell.ShellTheme;
import com.mirage.ui.shell.ShellWindow;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Duration;

/**
 * The red-team control panel. Pick a target in the lab range, choose a campaign,
 * set the stealth delay, and launch. Every launch opens its own terminal-style
 * shell popup that streams each step as it happens and ends with a banner
 * (SCAN COMPLETE / ACCESS GRANTED / NO ACCESS / CONNECTION BLOCKED / CONNECTION STALLED /
 * NO REPLY / REFUSED). "All at once" launches every technique concurrently and the shell
 * shows one pane per thread. Every launch goes through {@link Attacker}, which
 * refuses any target outside the authorised range before anything runs.
 */
public final class SpecterConsole extends JFrame {

    private static final String[] CAMPAIGNS = Campaigns.CHOICES;

    private final transient ScopeGuard guard;
    private final transient LabConfig config;
    private final JTextField hostField;
    private final JTextField portField;
    private final JComboBox<String> campaignBox = new JComboBox<>(CAMPAIGNS);
    private final JSlider stealthSlider = new JSlider(0, 5000, 0);
    private final JCheckBox concurrentBox = new JCheckBox("All at once (split threads)");
    private final JTextArea logArea = new JTextArea(10, 60);

    public SpecterConsole(ScopeGuard guard) {
        this(guard, LabConfig.defaults());
    }

    public SpecterConsole(ScopeGuard guard, LabConfig config) {
        super("Specter — attack simulator (lab range only)");
        this.guard = guard;
        this.config = config;
        this.hostField = new JTextField(config.consoleHost(), 12);
        this.portField = new JTextField(Integer.toString(config.httpDecoyPort()), 6);
        buildUi();
    }

    private void buildUi() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel targetRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        targetRow.add(new JLabel("Target:"));
        targetRow.add(hostField);
        targetRow.add(new JLabel("port"));
        targetRow.add(portField);

        JPanel campaignRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        campaignRow.add(new JLabel("Campaign:"));
        campaignRow.add(campaignBox);
        campaignRow.add(concurrentBox);

        JPanel stealthRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        stealthRow.add(new JLabel("Stealth delay (ms):"));
        stealthSlider.setMajorTickSpacing(1000);
        stealthSlider.setPaintTicks(true);
        stealthSlider.setPaintLabels(true);
        stealthRow.add(stealthSlider);

        JButton launch = new JButton("Launch");
        launch.addActionListener(e -> onLaunch());

        controls.add(targetRow);
        controls.add(campaignRow);
        controls.add(stealthRow);
        controls.add(Box.createVerticalStrut(6));
        controls.add(launch);

        logArea.setEditable(false);

        add(controls, BorderLayout.NORTH);
        add(new JScrollPane(logArea), BorderLayout.CENTER);
        setSize(760, 420);
        setLocationRelativeTo(null);
    }

    private void onLaunch() {
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            log("invalid port: '" + portField.getText().trim() + "' is not a number");
            return;
        }
        if (port < Target.MIN_PORT || port > Target.MAX_PORT) { // B-20: refuse before any shell opens
            log("invalid port: " + port + " (must be " + Target.MIN_PORT + "-" + Target.MAX_PORT + ")");
            return;
        }
        boolean concurrent = concurrentBox.isSelected();
        Campaign campaign = buildCampaign((String) campaignBox.getSelectedItem(), port);
        Duration stealth = Duration.ofMillis(stealthSlider.getValue());
        Target target = new Target(host, port);

        // Each launch gets its own shell popup, bus and attacker, so campaigns never cross-talk.
        TraceBus bus = new TraceBus();
        ShellWindow shell = new ShellWindow(ShellTheme.specter(), e -> true);
        bus.subscribe(shell::onTrace);
        Attacker attacker = new Attacker(guard, this::log);
        attacker.setTrace(bus, "attack");

        JFrame popup = openShell(campaign.name() + " → " + host + ":" + port
                + (concurrent ? "  [concurrent]" : ""), shell, attacker);
        if (concurrent) {
            shell.enableSplit();
        }

        try {
            if (concurrent) {
                attacker.launchConcurrent(campaign, target);
            } else {
                attacker.launch(campaign, target, stealth);
            }
            log("launched '" + campaign.name() + "' at " + host + ":" + port
                    + (concurrent ? " (all at once)" : " (stealth " + stealth.toMillis() + "ms)"));
        } catch (SecurityException e) {
            bus.line("attack", "scope", host, TraceLevel.BAD, "ScopeGuard refused " + host + ": " + e.getMessage());
            bus.line("attack", "scope", host, TraceLevel.BANNER, "REFUSED — OUTSIDE LAB RANGE");
            log("REFUSED: " + e.getMessage());
        }
        popup.setVisible(true);
    }

    private JFrame openShell(String title, ShellWindow shell, Attacker attacker) {
        JFrame frame = new JFrame("Specter shell — " + title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.add(shell);
        frame.setSize(860, 560);
        frame.setLocationByPlatform(true);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                attacker.shutdown();
                shell.close(); // B-33: stop the shell's 45 ms timer with its window
            }
        });
        return frame;
    }

    private Campaign buildCampaign(String choice, int port) {
        return Campaigns.forChoice(choice, config); // scan ports come from the config (B-21)
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
    }
}
