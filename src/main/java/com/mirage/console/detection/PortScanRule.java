package com.mirage.console.detection;

import com.mirage.common.*;

import java.util.Optional;

public final class PortScanRule implements DetectionRule {

    private static final int DISTINCT_SERVICES = 3;

    @Override
    public Optional<Classification> classify(AttackerProfile profile) {
        long services = profile.events().stream()
                .filter(e -> e.action() == Action.CONNECT || e.action() == Action.PORT_PROBE)
                .map(AttackEvent::decoyType)
                .distinct()
                .count();
        if (services >= DISTINCT_SERVICES) {
            return Optional.of(new Classification(
                    "Port / service scan", "T1046", Severity.MEDIUM));
        }
        return Optional.empty();
    }
}
