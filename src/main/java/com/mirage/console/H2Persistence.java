package com.mirage.console;

import com.mirage.common.Action;
import com.mirage.common.Alert;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerProfile;
import com.mirage.common.Honeytoken;
import com.mirage.common.LedgerEntry;
import com.mirage.common.ResponseCommand;
import com.mirage.console.db.AlertDao;
import com.mirage.console.db.BlockRuleDao;
import com.mirage.console.db.Database;
import com.mirage.console.db.EventDao;
import com.mirage.console.db.HoneytokenDao;
import com.mirage.console.db.ProfileDao;
import com.mirage.console.db.SensorDao;

import java.time.Instant;

/**
 * Persistence backed by the embedded H2 database: events, the signed ledger (with the id of
 * the key that signed it), and since B-30 the sensor, honeytoken, block_rule and
 * attacker_profile tables as well.
 */
public final class H2Persistence implements Persistence {

    private final EventDao events;
    private final AlertDao alerts;
    private final SensorDao sensors;
    private final HoneytokenDao honeytokens;
    private final BlockRuleDao blocks;
    private final ProfileDao profiles;
    private final String keyId;

    /** {@code keyId}: the fingerprint of the ledger key ({@link KeyPairSigner#keyId()}). */
    public H2Persistence(Database db, String keyId) {
        this.events = new EventDao(db);
        this.alerts = new AlertDao(db);
        this.sensors = new SensorDao(db);
        this.honeytokens = new HoneytokenDao(db);
        this.blocks = new BlockRuleDao(db);
        this.profiles = new ProfileDao(db);
        this.keyId = keyId;
    }

    @Override
    public void saveEvent(AttackEvent event) {
        events.save(event);
        Action action = event.action();
        if ((action == Action.LOGIN_ATTEMPT || action == Action.HONEYTOKEN_USE || action == Action.DECOY_ACCESS)
                && event.payload() != null) {
            honeytokens.used(event.payload(), event.timestamp()); // no-op unless it is a planted token
        }
    }

    @Override
    public void saveAlert(LedgerEntry entry) {
        alerts.save(entry, keyId);
    }

    @Override
    public void sensorSeen(String sensorId, String ip, Instant at) {
        sensors.seen(sensorId, ip, at);
    }

    @Override
    public void sensorOffline(String sensorId) {
        sensors.offline(sensorId);
    }

    @Override
    public void honeytokenPlanted(Honeytoken token) {
        honeytokens.planted(token);
    }

    @Override
    public void commandSent(ResponseCommand command) {
        blocks.record(command, Instant.now());
    }

    @Override
    public void profileAlerted(AttackerProfile profile, Alert alert) {
        profiles.alerted(profile, alert);
    }
}
