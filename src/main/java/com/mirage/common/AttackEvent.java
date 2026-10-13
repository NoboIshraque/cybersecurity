package com.mirage.common;

import java.time.Instant;

/**
 * One observed interaction with a decoy. Immutable. {@code sensorId} is the
 * Sensor that reported it (set by the Console from the connection); it is null
 * until tagged, so the 6-argument constructor keeps every producer unchanged.
 */
public record AttackEvent(
        String sourceIp,
        int sourcePort,
        String decoyType,
        Action action,
        String payload,
        Instant timestamp,
        String sensorId) {

    public AttackEvent(String sourceIp, int sourcePort, String decoyType, Action action,
                       String payload, Instant timestamp) {
        this(sourceIp, sourcePort, decoyType, action, payload, timestamp, null);
    }

    /** A copy tagged with the reporting Sensor's id. */
    public AttackEvent withSensor(String sensorId) {
        return new AttackEvent(sourceIp, sourcePort, decoyType, action, payload, timestamp, sensorId);
    }
}
