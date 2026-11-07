package com.mirage.console.db;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/** The {@code sensor} table: one row per Sensor id, kept current by its heartbeats (B-30). */
public final class SensorDao {

    private final Database db;

    public SensorDao(Database db) {
        this.db = db;
    }

    /** Insert or refresh the Sensor: its address, ONLINE, and the time of this heartbeat. */
    public synchronized void seen(String sensorId, String ip, Instant at) {
        String sql = "MERGE INTO sensor(hostname, ip, status, last_heartbeat) KEY(hostname) VALUES (?, ?, 'ONLINE', ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, sensorId);
            ps.setString(2, ip);
            ps.setTimestamp(3, Timestamp.from(at));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("failed to record sensor " + sensorId, e);
        }
    }

    /** The Sensor's link closed. */
    public synchronized void offline(String sensorId) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "UPDATE sensor SET status = 'OFFLINE' WHERE hostname = ?")) {
            ps.setString(1, sensorId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("failed to mark sensor " + sensorId + " offline", e);
        }
    }
}
