package com.mirage.console.db;

import com.mirage.common.CommandType;
import com.mirage.common.ResponseCommand;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * The {@code block_rule} table: every response command the Console sends (B-30). BLOCK, TARPIT
 * and FEED_FAKE add an active row (scope = the command); UNBLOCK closes the source's active rows.
 * The source's {@code attacker_profile.state} follows along.
 */
public final class BlockRuleDao {

    private final Database db;

    public BlockRuleDao(Database db) {
        this.db = db;
    }

    public synchronized void record(ResponseCommand command, Instant at) {
        try {
            if (command.type() == CommandType.UNBLOCK) {
                update("UPDATE block_rule SET active = FALSE WHERE src_ip = ? AND active", command.targetIp());
                update("UPDATE attacker_profile SET state = 'UNBLOCKED' WHERE src_ip = ?", command.targetIp());
                return;
            }
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "INSERT INTO block_rule(src_ip, created_ts, scope, active) VALUES (?, ?, ?, TRUE)")) {
                ps.setString(1, command.targetIp());
                ps.setTimestamp(2, Timestamp.from(at));
                ps.setString(3, command.type().name());
                ps.executeUpdate();
            }
            if (command.type() == CommandType.BLOCK) {
                update("UPDATE attacker_profile SET state = 'BLOCKED' WHERE src_ip = ?", command.targetIp());
            }
        } catch (SQLException e) {
            throw new RuntimeException("failed to record " + command.type() + " " + command.targetIp(), e);
        }
    }

    private void update(String sql, String ip) throws SQLException {
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, ip);
            ps.executeUpdate();
        }
    }
}
