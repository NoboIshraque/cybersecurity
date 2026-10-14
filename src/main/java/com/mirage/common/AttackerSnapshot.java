package com.mirage.common;

import java.util.List;

/**
 * An immutable, point-in-time copy of one source's observed events. The
 * correlation engine publishes these to the dashboard, so the UI thread reads
 * attacker activity without ever touching the live (mutable) {@link AttackerProfile}.
 */
public record AttackerSnapshot(String sourceIp, List<AttackEvent> events) {

    public AttackerSnapshot {
        events = List.copyOf(events);
    }
}
