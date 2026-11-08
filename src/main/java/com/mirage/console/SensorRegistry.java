package com.mirage.console;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks each Sensor's liveness: when it was last heard from (by the Console's clock, B-14) and
 * how many links it has open.
 *
 * <p>B-11: a Sensor whose link closed is <em>disconnected</em> (reported once, by whoever closes
 * the link), not "silent, possible tamper". A connected Sensor that stops heartbeating is
 * reported by {@link #newlySilent} once, and again only after it has been heard from since. Open
 * links are counted, so a Sensor that reconnects before its old link's close is noticed stays
 * connected.
 */
public final class SensorRegistry {

    private static final class State {
        Instant lastSeen;
        int links;
        boolean warnedSilent;
    }

    private final Map<String, State> sensors = new ConcurrentHashMap<>();

    private State state(String sensorId) {
        return sensors.computeIfAbsent(sensorId, k -> new State());
    }

    public synchronized void markSeen(String sensorId, Instant when) {
        State s = state(sensorId);
        s.lastSeen = when;
        s.warnedSilent = false;
    }

    public void markSeen(String sensorId) {
        markSeen(sensorId, Instant.now());
    }

    /** A link from this Sensor is up (its first heartbeat arrived on it). */
    public synchronized void linkOpened(String sensorId) {
        state(sensorId).links++;
    }

    /** A link from this Sensor closed; true when that was its last one (it is now disconnected). */
    public synchronized boolean linkClosed(String sensorId) {
        State s = sensors.get(sensorId);
        if (s == null || s.links == 0) {
            return false;
        }
        s.links--;
        return s.links == 0;
    }

    public synchronized boolean isConnected(String sensorId) {
        State s = sensors.get(sensorId);
        return s != null && s.links > 0;
    }

    /** Sensors with no heartbeat within {@code timeout} as of {@code now} (connected or not). */
    public synchronized List<String> deadSensors(Duration timeout, Instant now) {
        List<String> dead = new ArrayList<>();
        for (Map.Entry<String, State> entry : sensors.entrySet()) {
            Instant seen = entry.getValue().lastSeen;
            if (seen != null && Duration.between(seen, now).compareTo(timeout) > 0) {
                dead.add(entry.getKey());
            }
        }
        return dead;
    }

    /**
     * Connected Sensors that have gone silent for longer than {@code timeout} and were not reported
     * yet: each is returned once per silence (B-11). A disconnected Sensor is never in the list.
     */
    public synchronized List<String> newlySilent(Duration timeout, Instant now) {
        List<String> silent = new ArrayList<>();
        for (Map.Entry<String, State> entry : sensors.entrySet()) {
            State s = entry.getValue();
            if (s.links > 0 && !s.warnedSilent && s.lastSeen != null
                    && Duration.between(s.lastSeen, now).compareTo(timeout) > 0) {
                s.warnedSilent = true;
                silent.add(entry.getKey());
            }
        }
        return silent;
    }

    public boolean isKnown(String sensorId) {
        return sensors.containsKey(sensorId);
    }

    public int size() {
        return sensors.size();
    }
}
