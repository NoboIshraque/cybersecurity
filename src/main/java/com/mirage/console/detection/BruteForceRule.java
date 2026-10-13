package com.mirage.console.detection;

import com.mirage.common.*;

import java.util.Optional;

public final class BruteForceRule implements DetectionRule {

    private static final int THRESHOLD = 5;

    @Override
    public Optional<Classification> classify(AttackerProfile profile) {
        long attempts = profile.events().stream()
                .filter(e -> e.action() == Action.LOGIN_ATTEMPT)
                .count();
        if (attempts >= THRESHOLD) {
            return Optional.of(new Classification(
                    "Credential brute-force", "T1110", Severity.HIGH));
        }
        return Optional.empty();
    }
}
