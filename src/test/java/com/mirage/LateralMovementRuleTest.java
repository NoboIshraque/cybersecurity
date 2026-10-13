package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerProfile;
import com.mirage.console.detection.LateralMovementRule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class LateralMovementRuleTest {

    private static AttackEvent eventFrom(String sensorId) {
        return new AttackEvent("10.10.0.5", 4444, "http-login", Action.CONNECT, "", Instant.now())
                .withSensor(sensorId);
    }

    @Test
    void firesAcrossTwoSensors() {
        AttackerProfile profile = new AttackerProfile("10.10.0.5");
        profile.add(eventFrom("sensor-a"));
        profile.add(eventFrom("sensor-b"));

        var result = new LateralMovementRule().classify(profile);
        assertTrue(result.isPresent());
        assertEquals("T1021", result.get().attackTechnique());
    }

    @Test
    void quietOnASingleSensor() {
        AttackerProfile profile = new AttackerProfile("10.10.0.6");
        profile.add(eventFrom("sensor-a"));
        profile.add(eventFrom("sensor-a"));

        assertTrue(new LateralMovementRule().classify(profile).isEmpty());
    }
}
