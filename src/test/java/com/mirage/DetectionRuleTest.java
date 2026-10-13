package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerProfile;
import com.mirage.common.Classification;
import com.mirage.common.Honeytoken;
import com.mirage.common.Severity;
import com.mirage.console.HoneytokenStore;
import com.mirage.console.detection.BruteForceRule;
import com.mirage.console.detection.DetectionRule;
import com.mirage.console.detection.HoneytokenUseRule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DetectionRuleTest {

    private static AttackEvent event(Action action, String payload) {
        return new AttackEvent("10.10.0.5", 4444, "http-login", action, payload, Instant.now());
    }

    @Test
    void bruteForceFiresAtThreshold() {
        AttackerProfile profile = new AttackerProfile("10.10.0.5");
        DetectionRule rule = new BruteForceRule();

        for (int i = 0; i < 4; i++) {
            profile.add(event(Action.LOGIN_ATTEMPT, "admin:" + i));
        }
        assertTrue(rule.classify(profile).isEmpty(), "4 attempts is below the threshold");

        profile.add(event(Action.LOGIN_ATTEMPT, "admin:5")); // the 5th crosses the threshold
        Optional<Classification> result = rule.classify(profile);
        assertTrue(result.isPresent());
        assertEquals("T1110", result.get().attackTechnique());
        assertEquals(Severity.HIGH, result.get().severity());
    }

    @Test
    void honeytokenUseIsCritical() {
        HoneytokenStore store = new HoneytokenStore();
        store.plant(new Honeytoken("ht-1", "trap_user:trap_pass", "config/decoy.cfg"));

        AttackerProfile profile = new AttackerProfile("10.10.0.5");
        profile.add(event(Action.LOGIN_ATTEMPT, "trap_user:trap_pass"));

        Optional<Classification> result = new HoneytokenUseRule(store).classify(profile);
        assertTrue(result.isPresent());
        assertEquals(Severity.CRITICAL, result.get().severity());
    }

    @Test
    void ordinaryLoginIsNotAHoneytoken() {
        HoneytokenStore store = new HoneytokenStore();
        store.plant(new Honeytoken("ht-1", "trap_user:trap_pass", "config/decoy.cfg"));

        AttackerProfile profile = new AttackerProfile("10.10.0.6");
        profile.add(event(Action.LOGIN_ATTEMPT, "realuser:realpass"));

        assertTrue(new HoneytokenUseRule(store).classify(profile).isEmpty());
    }
}
