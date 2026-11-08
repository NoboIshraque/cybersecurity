package com.mirage.console.detection;

import com.mirage.common.AttackerProfile;
import com.mirage.common.Classification;
import com.mirage.common.Severity;

import java.util.Optional;

/**
 * Detects password spraying: a single password tried across many distinct
 * usernames (the inverse of brute force, and a common way to evade lockouts).
 */
public final class PasswordSprayRule implements DetectionRule {

    private static final int DISTINCT_USERS = 3;

    @Override
    public Optional<Classification> classify(AttackerProfile profile) {
        // The profile tracks accounts-per-password as logins arrive ("user:pass", split at the
        // first ':'), within a 10-minute window (B-28), so this is O(1) (B-02).
        return profile.recentAccountsOnOnePassword() >= DISTINCT_USERS
                ? Optional.of(new Classification("Password spraying", "T1110.003", Severity.HIGH))
                : Optional.empty();
    }
}
