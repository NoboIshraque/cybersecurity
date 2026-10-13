package com.mirage;

import com.mirage.console.SensorRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SensorRegistryTest {

    @Test
    void flagsSensorWithStaleHeartbeat() {
        SensorRegistry registry = new SensorRegistry();
        Instant t0 = Instant.parse("2026-10-13T10:00:00Z");
        registry.markSeen("fresh", t0.plusSeconds(55));
        registry.markSeen("stale", t0);

        List<String> dead = registry.deadSensors(Duration.ofSeconds(30), t0.plusSeconds(60));

        assertTrue(dead.contains("stale"));
        assertFalse(dead.contains("fresh"));
    }
}
