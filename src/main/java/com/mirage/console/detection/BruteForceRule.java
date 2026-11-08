package com.mirage.console.detection;

import com.mirage.common.*;

import java.util.Optional;

public final class BruteForceRule implements DetectionRule {

    private static final int THRESHOLD = 5;

    @Override
    public Optional<Classification> classify(AttackerProfile profile) {
        // B-28: different passwords against ONE account within a minute. A spray (one password,
        // many accounts) plus a later typo is not guessing, nor is a client retrying one password,
        // nor five attempts spread over a week. Specter's demo sends 8, so one lost attempt is fine.
        if (profile.recentPasswordsOnOneAccount() >= THRESHOLD) {
            return Optional.of(new Classification(
                    "Credential brute-force", "T1110", Severity.HIGH));
        }
        return Optional.empty();
    }
}
