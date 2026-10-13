package com.mirage.console.detection;

import com.mirage.common.*;

import java.util.Optional;

public final class HoneytokenUseRule implements DetectionRule {

    @Override
    public Optional<Classification> classify(AttackerProfile profile) {
        boolean used = profile.events().stream()
                .anyMatch(e -> e.action() == Action.HONEYTOKEN_USE);
        return used
                ? Optional.of(new Classification(
                        "Stolen credentials used", "T1078", Severity.CRITICAL))
                : Optional.empty();
    }
}
