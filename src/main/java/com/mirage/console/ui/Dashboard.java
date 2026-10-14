package com.mirage.console.ui;

import com.mirage.common.Alert;
import com.mirage.common.LedgerEntry;
import com.mirage.common.Severity;
import com.mirage.console.AlertLedger;
import com.mirage.console.AttackerDirectory;
import com.mirage.console.ResponseController;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntSupplier;

/**
 * The blue-team dashboard: a live network map, a severity-coloured alert feed, an
 * attacker dossier / session-replay / manual-response panel for the selected
 * source, a connection and ledger status line, and a one-click ledger integrity
 * check that highlights a broken link in red.
 */
public final class Dashboard extends JFrame {

    private final transient AlertLedger ledger;
    private final transient IntSupplier connectionCount;
    private final transient AttackerDirectory directory;

    private final AlertTableModel model = new AlertTableModel();
    private final JLabel status = new JLabel();
    private final NetworkMapPanel map;
    private final AttackerDetailPanel detail;
    private final JTable table = new JTable(model);
    private String ledgerState = "—";

    public Dashboard(AlertLedger ledger, IntSupplier connectionCount) {
        this(ledger, connectionCount, new AttackerDirectory(), new ResponseController(
                new com.mirage.common.ScopeGuard(List.of()), com.mirage.console.CommandSink.NONE));
    }

    public Dashboard(AlertLedger ledger, IntSupplier connectionCount,
                     AttackerDirectory directory, ResponseController responses) {
        super("Mirage Console — live");
        this.ledger = ledger;
        this.connectionCount = connectionCount;
        this.directory = directory;
        this.map = new NetworkMapPanel(this::select);
        this.detail = new AttackerDetailPanel(directory, responses);
        buildUi();
        new Timer(2000, e -> refreshStatus()).start();
    }

    private void buildUi() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JScrollPane mapScroll = new JScrollPane(map);
        mapScroll.setPreferredSize(new Dimension(1100, 128));
        mapScroll.setBorder(BorderFactory.createTitledBorder("Network map"));
        add(mapScroll, BorderLayout.NORTH);

        table.setDefaultRenderer(Object.class, new SeverityRenderer(model));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0 && row < model.getRowCount()) {
                    select(model.alertAt(row).sourceIp());
                }
            }
        });
        JScrollPane feed = new JScrollPane(table);
        feed.setBorder(BorderFactory.createTitledBorder("Alert feed"));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, feed, detail);
        split.setResizeWeight(0.52);
        add(split, BorderLayout.CENTER);

        JButton verify = new JButton("Verify ledger");
        verify.addActionListener(e -> verifyLedger());

        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.add(status, BorderLayout.CENTER);
        south.add(verify, BorderLayout.EAST);
        south.setBorder(BorderFactory.createEmptyBorder(2, 6, 4, 6));
        add(south, BorderLayout.SOUTH);

        setSize(1120, 680);
        setLocationRelativeTo(null);
        refreshStatus();
    }

    /** Called from the engine thread via the AlertBus; hops onto the EDT. */
    public void onAlert(Alert alert) {
        SwingUtilities.invokeLater(() -> {
            model.addAlert(alert);
            rebuildMap();
            refreshStatus();
        });
    }

    private void select(String sourceIp) {
        detail.showSource(sourceIp);
        map.setSelected(sourceIp);
    }

    private void verifyLedger() {
        int broken = ledger.firstBrokenIndex();
        if (broken < 0) {
            ledgerState = "verified";
            model.setBrokenAlert(null);
        } else {
            ledgerState = "BROKEN (entry " + broken + ")";
            List<LedgerEntry> entries = ledger.entries();
            model.setBrokenAlert(broken < entries.size() ? entries.get(broken).alert() : null);
        }
        refreshStatus();
    }

    private void rebuildMap() {
        Map<String, Severity> worst = new TreeMap<>();
        Map<String, Integer> counts = new TreeMap<>();
        for (Alert a : model.alerts()) {
            String ip = a.sourceIp();
            Severity sev = a.classification().severity();
            worst.merge(ip, sev, (x, y) -> x.ordinal() >= y.ordinal() ? x : y);
            counts.merge(ip, 1, Integer::sum);
        }
        Set<String> all = new LinkedHashSet<>(worst.keySet());
        all.addAll(directory.sources());

        List<NetworkMapPanel.Node> nodes = new ArrayList<>();
        for (String ip : all) {
            nodes.add(new NetworkMapPanel.Node(ip, worst.get(ip), counts.getOrDefault(ip, 0)));
        }
        nodes.sort(Comparator
                .comparingInt((NetworkMapPanel.Node n) -> n.severity() == null ? -1 : n.severity().ordinal())
                .reversed()
                .thenComparing(NetworkMapPanel.Node::sourceIp));
        map.setNodes(nodes);
    }

    private void refreshStatus() {
        status.setText("Alerts: " + model.getRowCount()
                + "    |    Sensors connected: " + connectionCount.getAsInt()
                + "    |    Ledger: " + ledgerState);
    }

    private static final class SeverityRenderer extends DefaultTableCellRenderer {

        private final transient AlertTableModel model;

        SeverityRenderer(AlertTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            if (row >= 0 && row < model.getRowCount()) {
                if (model.isBrokenRow(row)) {
                    c.setBackground(new Color(0xFF, 0x52, 0x52));
                    c.setForeground(Color.WHITE);
                    return c;
                }
                c.setForeground(Color.BLACK);
                if (!selected) {
                    c.setBackground(colorFor(model.alertAt(row).classification().severity()));
                }
            }
            return c;
        }

        private static Color colorFor(Severity severity) {
            return switch (severity) {
                case CRITICAL -> new Color(0xFF, 0xCD, 0xD2);
                case HIGH -> new Color(0xFF, 0xE0, 0xB2);
                case MEDIUM -> new Color(0xFF, 0xF9, 0xC4);
                case LOW -> new Color(0xE8, 0xF5, 0xE9);
            };
        }
    }
}
