package com.mirage.console.ui;

import com.mirage.common.Alert;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** Backs the dashboard's alert table. Newest alert first. */
public final class AlertTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Time", "Source", "Classification", "ATT&CK", "Severity"};

    private final List<Alert> alerts = new ArrayList<>();

    public void addAlert(Alert alert) {
        alerts.add(0, alert); // newest first
        fireTableRowsInserted(0, 0);
    }

    public Alert alertAt(int row) {
        return alerts.get(row);
    }

    @Override
    public int getRowCount() {
        return alerts.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        Alert alert = alerts.get(row);
        return switch (column) {
            case 0 -> alert.timestamp().toString();
            case 1 -> alert.sourceIp();
            case 2 -> alert.classification().label();
            case 3 -> alert.classification().attackTechnique();
            case 4 -> alert.classification().severity().name();
            default -> "";
        };
    }
}
