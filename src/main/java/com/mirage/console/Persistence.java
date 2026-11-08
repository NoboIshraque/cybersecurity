package com.mirage.console;

import com.mirage.common.Alert;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerProfile;
import com.mirage.common.Honeytoken;
import com.mirage.common.LedgerEntry;
import com.mirage.common.ResponseCommand;

import java.time.Instant;

/**
 * Where the Console records what it sees and does. {@link #NONE} is an in-memory no-op; the
 * optional records (B-30) default to no-ops so a test double only implements what it needs.
 */
public interface Persistence {

    void saveEvent(AttackEvent event);

    void saveAlert(LedgerEntry entry);

    /** A Sensor heartbeated from {@code ip}. */
    default void sensorSeen(String sensorId, String ip, Instant at) { }

    /** A Sensor's link closed. */
    default void sensorOffline(String sensorId) { }

    /** A honeytoken was planted (recorded once, however often the Console restarts). */
    default void honeytokenPlanted(Honeytoken token) { }

    /** A response command was sent to the Sensors. */
    default void commandSent(ResponseCommand command) { }

    /** {@code profile} just raised {@code alert}: refresh its summary. */
    default void profileAlerted(AttackerProfile profile, Alert alert) { }

    Persistence NONE = new Persistence() {
        @Override public void saveEvent(AttackEvent event) { }
        @Override public void saveAlert(LedgerEntry entry) { }
    };
}
