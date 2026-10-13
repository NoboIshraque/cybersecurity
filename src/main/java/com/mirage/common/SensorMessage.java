package com.mirage.common;

import java.time.Instant;

/**
 * One line on the Sensor-to-Console wire. An EVENT carries an {@link AttackEvent};
 * a HEARTBEAT carries only the sensor id. Serialised as a single JSON line.
 */
public record SensorMessage(MessageType type, AttackEvent event, String sensorId, Instant timestamp) {

    public static SensorMessage event(AttackEvent event) {
        return new SensorMessage(MessageType.EVENT, event, null, event.timestamp());
    }

    public static SensorMessage heartbeat(String sensorId) {
        return new SensorMessage(MessageType.HEARTBEAT, null, sensorId, Instant.now());
    }
}
