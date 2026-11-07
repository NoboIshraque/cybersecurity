package com.mirage.console.detection;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerProfile;
import com.mirage.common.Classification;
import com.mirage.common.Severity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Detects password spraying: a single password tried across many distinct
 * usernames (the inverse of brute force, and a common way to evade lockouts).
 */
public final class PasswordSprayRule implements DetectionRule {

    private static final int DISTINCT_USERS = 3;

    @Override
    public Optional<Classification> classify(AttackerProfile profile) {
        Map<String, Set<String>> usersByPassword = new HashMap<>();
        for (AttackEvent e : profile.events()) {
            if (e.action() != Action.LOGIN_ATTEMPT || e.payload() == null) {
                continue;
            }
            int colon = e.payload().indexOf(':');
            if (colon < 0) {
                continue;
            }
            String user = e.payload().substring(0, colon);
            String password = e.payload().substring(colon + 1);
            usersByPassword.computeIfAbsent(password, k -> new HashSet<>()).add(user);
        }
        boolean spray = usersByPassword.values().stream().anyMatch(s -> s.size() >= DISTINCT_USERS);
        return spray
                ? Optional.of(new Classification("Password spraying", "T1110.003", Severity.HIGH))
                : Optional.empty();
    }
}
