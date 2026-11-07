package com.mirage.console.db;

import com.mirage.common.AttackEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

/** Persists observed attack events. */
public final class EventDao {

    private final Database db;

    public EventDao(Database db) {
        this.db = db;
    }

    public synchronized void save(AttackEvent event) {
        // B-30: also the decoy type, and the reporting Sensor's row (null until it has heartbeated)
        String sql = "INSERT INTO attack_event(sensor_id, src_ip, ts, action, payload, decoy_type) "
                + "VALUES ((SELECT MIN(id) FROM sensor WHERE hostname = ?), ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, event.sensorId());
            ps.setString(2, event.sourceIp());
            ps.setTimestamp(3, Timestamp.from(event.timestamp()));
            ps.setString(4, event.action().name());
            ps.setString(5, event.payload());
            ps.setString(6, event.decoyType());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("failed to save event", e);
        }
    }

    public synchronized int count() {
        try (Statement st = db.connection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM attack_event")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException("failed to count events", e);
        }
    }
}
