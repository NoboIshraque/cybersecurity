package com.mirage.console;

import com.mirage.common.AttackEvent;

import java.util.Optional;

/**
 * Checks an {@link AttackEvent} as it arrives at the Console, before it is queued for the
 * {@link CorrelationEngine}. An event with no source, action, decoy or timestamp, or with
 * fields longer than the database columns, is refused here instead of failing deeper in
 * the pipeline (B-01).
 */
public final class EventValidator {

    /** {@code attack_event.src_ip VARCHAR(45)}: the longest textual IPv6 address. */
    public static final int MAX_SOURCE = 45;
    /** {@code decoy.type VARCHAR(32)}. */
    public static final int MAX_DECOY = 32;
    /** Payloads are evidence, so a longer one is cut, not refused. */
    public static final int MAX_PAYLOAD = 4096;

    private EventValidator() {
    }

    /** Why the event can't be processed, or empty if it can. */
    public static Optional<String> problem(AttackEvent event) {
        if (event == null) {
            return Optional.of("no event");
        }
        if (event.sourceIp() == null || event.sourceIp().isBlank()) {
            return Optional.of("no source address");
        }
        if (event.sourceIp().length() > MAX_SOURCE) {
            return Optional.of("source address longer than " + MAX_SOURCE + " characters");
        }
        if (event.action() == null) {
            return Optional.of("no action");
        }
        if (event.decoyType() == null || event.decoyType().isBlank()) {
            return Optional.of("no decoy type");
        }
        if (event.decoyType().length() > MAX_DECOY) {
            return Optional.of("decoy type longer than " + MAX_DECOY + " characters");
        }
        if (event.timestamp() == null) {
            return Optional.of("no timestamp");
        }
        return Optional.empty();
    }

    /** A missing payload becomes {@code ""}; one over {@link #MAX_PAYLOAD} is cut to that length. */
    public static AttackEvent normalise(AttackEvent event) {
        String payload = event.payload() == null ? "" : event.payload();
        if (payload.length() > MAX_PAYLOAD) {
            payload = payload.substring(0, MAX_PAYLOAD);
        }
        if (payload.equals(event.payload())) {
            return event;
        }
        return new AttackEvent(event.sourceIp(), event.sourcePort(), event.decoyType(), event.action(),
                payload, event.timestamp(), event.sensorId());
    }
}
