package com.mirage.console.ui;

import com.mirage.common.Alert;
import com.mirage.common.Severity;
import com.mirage.console.AlertLedger;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.function.IntSupplier;

/**
 * The blue-team dashboard: a live, severity-coloured alert feed, a connection/
 * ledger status line, and a one-click ledger integrity check.
 */
public final class Dashboard extends JFrame {

    private final transient AlertLedger ledger;
    private final transient IntSupplier connectionCount;
    private final AlertTableModel model = new AlertTableModel();
    private final JLabel status = new JLabel();

    public Dashboard(AlertLedger ledger, IntSupplier connectionCount) {
        super("Mirage Console — live");
        this.ledger = ledger;
        this.connectionCount = connectionCount;
        buildUi();
        new Timer(2000, e -> refreshStatus()).start();
    }

    private void buildUi() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JTable table = new JTable(model);
        table.setDefaultRenderer(Object.class, new SeverityRenderer(model));
        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton verify = new JButton("Verify ledger");
        verify.addActionListener(e -> status.setText(statusText(ledger.verify() ? "verified" : "BROKEN")));

        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.add(status, BorderLayout.CENTER);
        south.add(verify, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);

        setSize(840, 540);
        setLocationRelativeTo(null);
        refreshStatus();
    }

    /** Called from the engine thread via the AlertBus; hops onto the EDT. */
    public void onAlert(Alert alert) {
        SwingUtilities.invokeLater(() -> {
            model.addAlert(alert);
            refreshStatus();
        });
    }

    private void refreshStatus() {
        status.setText(statusText("—"));
    }

    private String statusText(String ledgerState) {
        return "Alerts: " + model.getRowCount()
                + "    |    Sensors connected: " + connectionCount.getAsInt()
                + "    |    Ledger: " + ledgerState;
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
            if (!selected && row >= 0 && row < model.getRowCount()) {
                c.setBackground(colorFor(model.alertAt(row).classification().severity()));
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
