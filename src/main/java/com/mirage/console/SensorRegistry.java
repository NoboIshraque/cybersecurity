package com.mirage.console;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks when each Sensor was last heard from, so dead Sensors can be detected. */
public final class SensorRegistry {

    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

    public void markSeen(String sensorId, Instant when) {
        lastSeen.put(sensorId, when);
    }

    public void markSeen(String sensorId) {
        markSeen(sensorId, Instant.now());
    }

    /** Sensors with no heartbeat within {@code timeout} as of {@code now}. */
    public List<String> deadSensors(Duration timeout, Instant now) {
        List<String> dead = new ArrayList<>();
        for (Map.Entry<String, Instant> entry : lastSeen.entrySet()) {
            if (Duration.between(entry.getValue(), now).compareTo(timeout) > 0) {
                dead.add(entry.getKey());
            }
        }
        return dead;
    }

    public boolean isKnown(String sensorId) {
        return lastSeen.containsKey(sensorId);
    }

    public int size() {
        return lastSeen.size();
    }
}
