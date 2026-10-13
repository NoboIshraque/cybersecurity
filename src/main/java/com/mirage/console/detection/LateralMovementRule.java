package com.mirage.console.detection;

import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerProfile;
import com.mirage.common.Classification;
import com.mirage.common.Severity;

import java.util.Optional;

/**
 * Cross-host correlation: one source touching decoys on two or more distinct
 * Sensors is coordinated activity (scanning the network, or lateral movement).
 */
public final class LateralMovementRule implements DetectionRule {

    private static final int DISTINCT_SENSORS = 2;

    @Override
    public Optional<Classification> classify(AttackerProfile profile) {
        long sensors = profile.events().stream()
                .map(AttackEvent::sensorId)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .count();
        return sensors >= DISTINCT_SENSORS
                ? Optional.of(new Classification(
                        "Multi-host activity (lateral movement)", "T1021", Severity.HIGH))
                : Optional.empty();
    }
}
