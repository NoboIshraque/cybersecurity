package com.mirage.console.db;

import com.mirage.common.Alert;
import com.mirage.common.AttackerProfile;
import com.mirage.common.Severity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The {@code attacker_profile} table: one summary row per source, updated each time it raises an
 * alert (not on every event, to keep the engine fast): first/last seen, events so far, every
 * technique detected and the worst severity (B-30).
 */
public final class ProfileDao {

    private final Database db;

    public ProfileDao(Database db) {
        this.db = db;
    }

    public synchronized void alerted(AttackerProfile profile, Alert alert) {
        String ip = profile.sourceIp();
        Set<String> techniques = new LinkedHashSet<>();
        Severity worst = alert.classification().severity();
        String state = "ALERTED";
        try {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT techniques, severity, state FROM attacker_profile WHERE src_ip = ?")) {
                ps.setString(1, ip);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        if (rs.getString(1) != null && !rs.getString(1).isBlank()) {
                            techniques.addAll(java.util.List.of(rs.getString(1).split(",")));
                        }
                        Severity before = parse(rs.getString(2));
                        if (before != null && before.ordinal() > worst.ordinal()) {
                            worst = before;
                        }
                        if (rs.getString(3) != null) {
                            state = rs.getString(3); // keep BLOCKED / UNBLOCKED
                        }
                    }
                }
            }
            techniques.add(alert.classification().attackTechnique());
            String sql = "MERGE INTO attacker_profile(src_ip, first_seen, last_seen, event_count, techniques, "
                    + "severity, state) KEY(src_ip) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
                ps.setString(1, ip);
                ps.setTimestamp(2, timestamp(profile.firstSeen(), alert.timestamp()));
                ps.setTimestamp(3, timestamp(profile.lastSeen(), alert.timestamp()));
                ps.setInt(4, (int) Math.min(Integer.MAX_VALUE, profile.eventCount()));
                ps.setString(5, truncate(String.join(",", techniques), 512));
                ps.setString(6, worst.name());
                ps.setString(7, state);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("failed to update the profile of " + ip, e);
        }
    }

    private static Severity parse(String stored) {
        try {
            return stored == null ? null : Severity.valueOf(stored);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Timestamp timestamp(Instant value, Instant fallback) {
        return Timestamp.from(value != null ? value : fallback);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
