package com.mirage;

import com.mirage.common.Alert;
import com.mirage.common.Classification;
import com.mirage.common.Severity;
import com.mirage.console.ui.AlertTableModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AlertTableModelTest {

    @Test
    void addsNewestFirstWithExpectedColumns() {
        AlertTableModel model = new AlertTableModel();
        model.addAlert(new Alert("10.0.0.1",
                new Classification("Port scan", "T1046", Severity.MEDIUM),
                Instant.parse("2026-10-13T10:00:00Z")));
        model.addAlert(new Alert("10.0.0.2",
                new Classification("Credential brute-force", "T1110", Severity.HIGH),
                Instant.parse("2026-10-13T10:01:00Z")));

        assertEquals(2, model.getRowCount());
        assertEquals(5, model.getColumnCount());
        // newest first
        assertEquals("10.0.0.2", model.getValueAt(0, 1));
        assertEquals("T1110", model.getValueAt(0, 3));
        assertEquals("HIGH", model.getValueAt(0, 4));
        assertEquals("10.0.0.1", model.getValueAt(1, 1));
    }
}
