package com.mirage.console.detection;

import com.mirage.common.*;

import java.util.Optional;

public final class BruteForceRule implements DetectionRule {

    private static final int THRESHOLD = 5;

    @Override
    public Optional<Classification> classify(AttackerProfile profile) {
        if (profile.count(Action.LOGIN_ATTEMPT) >= THRESHOLD) { // lifetime counter, O(1) (B-02)
            return Optional.of(new Classification(
                    "Credential brute-force", "T1110", Severity.HIGH));
        }
        return Optional.empty();
    }
}
