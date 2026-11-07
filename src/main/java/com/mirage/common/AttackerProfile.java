package com.mirage.common;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The running dossier for one source address. Mutated only by the single
 * correlation consumer thread, so it needs no internal synchronisation.
 *
 * <p>B-02: {@link #add} updates lifetime counters and sets once per event, so the detection
 * rules read them in constant time instead of re-scanning the whole history on every event.
 * Only the most recent {@link #HISTORY} events are kept as history (for the dashboard's replay).
 * The distinct-value sets grow only with distinct values (new credentials, services, sensors),
 * never with repeats; they are deliberately not capped, so a flood of junk logins can't push a
 * honeytoken out of sight.
 */
public final class AttackerProfile {

    /** Events kept as history, most recent last. */
    public static final int HISTORY = 500;

    private final String sourceIp;
    private final Deque<AttackEvent> history = new ArrayDeque<>();
    private final Map<Action, Long> counts = new EnumMap<>(Action.class);
    private final Set<String> probedServices = new LinkedHashSet<>();
    private final Set<String> sensors = new LinkedHashSet<>();
    private final Set<String> credentials = new HashSet<>();
    private final Map<String, Set<String>> usersByPassword = new HashMap<>();
    private int mostUsersForOnePassword;
    private long eventCount;
    private Instant firstSeen;
    private Instant lastSeen;

    public AttackerProfile(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public void add(AttackEvent event) {
        history.addLast(event);
        if (history.size() > HISTORY) {
            history.removeFirst();
        }
        eventCount++;
        Instant at = event.timestamp();
        if (at != null) {
            firstSeen = firstSeen == null || at.isBefore(firstSeen) ? at : firstSeen;
            lastSeen = lastSeen == null || at.isAfter(lastSeen) ? at : lastSeen;
        }
        Action action = event.action();
        if (action == null) {
            return;
        }
        counts.merge(action, 1L, Long::sum);
        if ((action == Action.CONNECT || action == Action.PORT_PROBE) && event.decoyType() != null) {
            probedServices.add(event.decoyType());
        }
        if (event.sensorId() != null && !event.sensorId().isBlank()) {
            sensors.add(event.sensorId());
        }
        if (action == Action.LOGIN_ATTEMPT && event.payload() != null) {
            credentials.add(event.payload());
            int colon = event.payload().indexOf(':');
            if (colon >= 0) {
                Set<String> users = usersByPassword.computeIfAbsent(
                        event.payload().substring(colon + 1), k -> new HashSet<>());
                users.add(event.payload().substring(0, colon));
                mostUsersForOnePassword = Math.max(mostUsersForOnePassword, users.size());
            }
        }
    }

    public String sourceIp() {
        return sourceIp;
    }

    /** The most recent events (at most {@link #HISTORY}), oldest first. A copy. */
    public List<AttackEvent> events() {
        return List.copyOf(history);
    }

    /** Every event this source has produced, including those no longer in {@link #events()}. */
    public long eventCount() {
        return eventCount;
    }

    /** Lifetime count of events with this action. */
    public long count(Action action) {
        return counts.getOrDefault(action, 0L);
    }

    /** Distinct decoy types this source connected to or probed. */
    public Set<String> probedServices() {
        return Collections.unmodifiableSet(probedServices);
    }

    /** Distinct Sensors that reported this source. */
    public Set<String> sensors() {
        return Collections.unmodifiableSet(sensors);
    }

    /** Whether this source ever tried exactly these login credentials ("user:pass"). */
    public boolean triedCredentials(String credentials) {
        return this.credentials.contains(credentials);
    }

    /** The most distinct usernames tried with any single password (password spraying). */
    public int mostUsersForOnePassword() {
        return mostUsersForOnePassword;
    }

    /** Timestamp of the earliest event, or {@code null} before the first. */
    public Instant firstSeen() {
        return firstSeen;
    }

    /** Timestamp of the latest event, or {@code null} before the first. */
    public Instant lastSeen() {
        return lastSeen;
    }
}
