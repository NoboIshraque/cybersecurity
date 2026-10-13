package com.mirage.common;

import java.time.Instant;

/** One observed interaction with a decoy. Immutable value object. */
public record AttackEvent(
        String sourceIp,
        int sourcePort,
        String decoyType,
        Action action,
        String payload,
        Instant timestamp) {
}
