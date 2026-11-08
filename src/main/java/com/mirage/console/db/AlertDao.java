package com.mirage.console.db;

import com.mirage.common.Alert;
import com.mirage.common.Classification;
import com.mirage.common.LedgerEntry;
import com.mirage.common.Severity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Persists classified alerts, including the hash-chain links and the signature. */
public final class AlertDao {

    private final Database db;

    public AlertDao(Database db) {
        this.db = db;
    }

    /** Without a key id: such a row is "legacy" and is not loaded back into the chain. */
    public synchronized void save(LedgerEntry entry) {
        save(entry, null);
    }

    /**
     * Save an entry signed by the key {@code keyId}. The exact alert timestamp is kept as text
     * ({@code alert_ts}), because the hash covers its full precision and a SQL TIMESTAMP may not.
     */
    public synchronized void save(LedgerEntry entry, String keyId) {
        Alert alert = entry.alert();
        String sql = "INSERT INTO alert(src_ip, ts, classification, attack_technique, severity, "
                + "prev_hash, hash, signature, key_id, alert_ts) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, alert.sourceIp());
            ps.setTimestamp(2, Timestamp.from(alert.timestamp()));
            ps.setString(3, alert.classification().label());
            ps.setString(4, alert.classification().attackTechnique());
            ps.setString(5, alert.classification().severity().name());
            ps.setString(6, entry.prevHash());
            ps.setString(7, entry.hash());
            ps.setString(8, entry.signature());
            ps.setString(9, keyId);
            ps.setString(10, alert.timestamp().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("failed to save alert", e);
        }
    }

    /**
     * The stored chain, oldest first: every row with a key id (B-30). Rows are rebuilt as they
     * are stored, never "repaired": a value edited in the database (even into something
     * unparsable) still loads, so verification can flag it instead of the Console failing.
     */
    public synchronized List<LedgerEntry> loadChain() {
        String sql = "SELECT src_ip, classification, attack_technique, severity, alert_ts, prev_hash, hash, signature "
                + "FROM alert WHERE key_id IS NOT NULL ORDER BY id";
        List<LedgerEntry> chain = new ArrayList<>();
        try (Statement st = db.connection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Alert alert = new Alert(rs.getString(1),
                        new Classification(rs.getString(2), rs.getString(3), severity(rs.getString(4))),
                        instant(rs.getString(5)));
                chain.add(new LedgerEntry(trim(rs.getString(6)), trim(rs.getString(7)), alert,
                        rs.getString(8) == null ? "" : rs.getString(8)));
            }
        } catch (SQLException e) {
            throw new RuntimeException("failed to load the ledger", e);
        }
        return chain;
    }

    /** Rows written before ledger keys were saved: kept, but their signatures can't be checked. */
    public synchronized int legacyCount() {
        try (Statement st = db.connection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM alert WHERE key_id IS NULL")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException("failed to count legacy alerts", e);
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

    /** An edited severity that is not a Severity loads as LOW, and fails the hash check. */
    private static Severity severity(String stored) {
        try {
            return Severity.valueOf(stored);
        } catch (RuntimeException e) {
            return Severity.LOW;
        }
    }

    private static Instant instant(String stored) {
        try {
            return Instant.parse(stored);
        } catch (RuntimeException e) {
            return Instant.EPOCH;
        }
    }

    private static String trim(String hash) {
        return hash == null ? "" : hash.trim();
    }
}
