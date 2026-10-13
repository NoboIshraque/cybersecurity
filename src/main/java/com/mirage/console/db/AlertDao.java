package com.mirage.console.db;

import com.mirage.common.Alert;
import com.mirage.common.LedgerEntry;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

/** Persists classified alerts, including the hash-chain links and the signature. */
public final class AlertDao {

    private final Database db;

    public AlertDao(Database db) {
        this.db = db;
    }

    public synchronized void save(LedgerEntry entry) {
        Alert alert = entry.alert();
        String sql = "INSERT INTO alert(src_ip, ts, classification, attack_technique, severity, "
                + "prev_hash, hash, signature) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, alert.sourceIp());
            ps.setTimestamp(2, Timestamp.from(alert.timestamp()));
            ps.setString(3, alert.classification().label());
            ps.setString(4, alert.classification().attackTechnique());
            ps.setString(5, alert.classification().severity().name());
            ps.setString(6, entry.prevHash());
            ps.setString(7, entry.hash());
            ps.setString(8, entry.signature());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("failed to save alert", e);
        }
    }

    public synchronized int count() {
        try (Statement st = db.connection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM alert")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException("failed to count alerts", e);
        }
    }
}
