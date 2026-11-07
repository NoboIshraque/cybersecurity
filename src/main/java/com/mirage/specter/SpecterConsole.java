package com.mirage.specter;

import com.mirage.common.LabConfig;
import com.mirage.common.ScopeGuard;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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
import java.time.Duration;
import java.util.List;

/**
 * The red-team control panel. Pick a target in the lab range, choose a campaign,
 * set the stealth delay, and launch. Every launch goes through {@link Attacker},
 * which refuses any target outside the authorised range.
 */
public final class SpecterConsole extends JFrame {

    private static final String[] CAMPAIGNS = {
            "Recon scan", "Brute force", "Password spray", "Honeytoken replay", "Full campaign"
    };

    private final transient Attacker attacker;
    private final JTextField hostField;
    private final JTextField portField;
    private final JComboBox<String> campaignBox = new JComboBox<>(CAMPAIGNS);
    private final JSlider stealthSlider = new JSlider(0, 5000, 0);
    private final JTextArea logArea = new JTextArea(18, 60);

    public SpecterConsole(ScopeGuard guard) {
        this(guard, LabConfig.defaults());
    }

    public SpecterConsole(ScopeGuard guard, LabConfig config) {
        super("Specter — attack simulator (lab range only)");
        this.attacker = new Attacker(guard, this::log);
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
        setSize(760, 520);
        setLocationRelativeTo(null);
    }

    private void onLaunch() {
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            log("invalid port");
            return;
        }
        Campaign campaign = buildCampaign((String) campaignBox.getSelectedItem(), port);
        Duration stealth = Duration.ofMillis(stealthSlider.getValue());
        try {
            attacker.launch(campaign, new Target(host, port), stealth);
            log("launched '" + campaign.name() + "' at " + host + ":" + port
                    + " (stealth " + stealth.toMillis() + "ms)");
        } catch (SecurityException e) {
            log("REFUSED: " + e.getMessage());
        }
    }

    private Campaign buildCampaign(String choice, int port) {
        return switch (choice) {
            case "Recon scan" -> new Campaign("recon", List.of(new PortScanAttack(8020, 8085)));
            case "Brute force" -> new Campaign("brute", List.of(
                    new BruteForceAttack(List.of("123456", "password", "admin", "letmein", "root"))));
            case "Password spray" -> new Campaign("spray", List.of(
                    new PasswordSprayAttack(List.of("admin", "root", "user", "test"), "Password123!")));
            case "Honeytoken replay" -> new Campaign("honeytoken", List.of(
                    new HoneytokenReplayAttack("svc_backup:S3cr3tBackup!")));
            default -> new Campaign("full", List.of(
                    new PortScanAttack(8020, 8085),
                    new BruteForceAttack(List.of("123456", "password", "admin", "letmein", "root")),
                    new PasswordSprayAttack(List.of("admin", "root", "user", "test"), "Password123!"),
                    new HoneytokenReplayAttack("svc_backup:S3cr3tBackup!")));
        };
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
    }
}
