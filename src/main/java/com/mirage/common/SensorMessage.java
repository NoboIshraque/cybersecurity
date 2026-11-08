package com.mirage.common;

import java.time.Instant;

/**
 * One line on the Sensor-to-Console wire. An EVENT carries an {@link AttackEvent};
 * a HEARTBEAT carries only the sensor id; an ACK carries the sensor id and, in
 * {@code note}, the command it just applied. Serialised as a single JSON line.
 */
public record SensorMessage(MessageType type, AttackEvent event, String sensorId, Instant timestamp,
                            String note) {

    public static SensorMessage event(AttackEvent event) {
        return new SensorMessage(MessageType.EVENT, event, null, event.timestamp(), null);
    }

    public static SensorMessage heartbeat(String sensorId) {
        return new SensorMessage(MessageType.HEARTBEAT, null, sensorId, Instant.now(), null);
    }

    public static SensorMessage ack(String sensorId, ResponseCommand applied) {
        return new SensorMessage(MessageType.ACK, null, sensorId, Instant.now(),
                applied.type() + " " + applied.targetIp());
    }
}
