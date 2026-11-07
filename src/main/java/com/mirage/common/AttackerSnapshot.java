package com.mirage.common;

import java.time.Instant;
import java.util.List;

/**
 * An immutable copy of one source's activity for the dashboard: its recent events (at most
 * {@link AttackerProfile#HISTORY}) plus lifetime totals, so the dossier can report the whole
 * lifetime without the snapshot copying the whole history on every event (B-02).
 */
public record AttackerSnapshot(String sourceIp, List<AttackEvent> events, long totalEvents, Instant firstSeen) {

    public AttackerSnapshot {
        events = List.copyOf(events);
    }

    /** A snapshot whose history is its whole lifetime. */
    public AttackerSnapshot(String sourceIp, List<AttackEvent> events) {
        this(sourceIp, events, events.size(), events.stream()
                .map(AttackEvent::timestamp).filter(t -> t != null).min(Instant::compareTo).orElse(null));
    }

    /** A snapshot of a live profile: its history and its lifetime totals. */
    public static AttackerSnapshot of(AttackerProfile profile) {
        return new AttackerSnapshot(profile.sourceIp(), profile.events(), profile.eventCount(), profile.firstSeen());
    }
}
