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
        String sql = "INSERT INTO attack_event(src_ip, ts, action, payload) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, event.sourceIp());
            ps.setTimestamp(2, Timestamp.from(event.timestamp()));
            ps.setString(3, event.action().name());
            ps.setString(4, event.payload());
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
