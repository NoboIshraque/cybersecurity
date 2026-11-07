package com.mirage.console.db;

import com.mirage.common.Honeytoken;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/** The {@code honeytoken} table: what was planted where, and when it was first used (B-30). */
public final class HoneytokenDao {

    private final Database db;

    public HoneytokenDao(Database db) {
        this.db = db;
    }

    /** Record a planted token once; a restart planting the same token again adds nothing. */
    public synchronized void planted(Honeytoken token) {
        String sql = "INSERT INTO honeytoken(token_value, planted_location) SELECT ?, ? "
                + "WHERE NOT EXISTS (SELECT 1 FROM honeytoken WHERE token_value = ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, token.credentials());
            ps.setString(2, token.plantedLocation());
            ps.setString(3, token.credentials());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("failed to record honeytoken " + token.id(), e);
        }
    }

    /** Mark the token with these credentials used, the first time only. No-op for other credentials. */
    public synchronized void used(String credentials, Instant at) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "UPDATE honeytoken SET used = TRUE, used_ts = ? WHERE token_value = ? AND used = FALSE")) {
            ps.setTimestamp(1, Timestamp.from(at));
            ps.setString(2, credentials);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("failed to mark a honeytoken used", e);
        }
    }
}
