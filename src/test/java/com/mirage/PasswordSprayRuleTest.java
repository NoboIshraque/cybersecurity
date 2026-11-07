package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerProfile;
import com.mirage.console.detection.PasswordSprayRule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PasswordSprayRuleTest {

    private static AttackEvent login(String creds) {
        return new AttackEvent("10.10.0.5", 4444, "ftp", Action.LOGIN_ATTEMPT, creds, Instant.now());
    }

    @Test
    void firesWhenOnePasswordHitsManyUsers() {
        AttackerProfile profile = new AttackerProfile("10.10.0.5");
        for (String user : List.of("alice", "bob", "carol")) {
            profile.add(login(user + ":Winter2026!"));
        }
        var result = new PasswordSprayRule().classify(profile);
        assertTrue(result.isPresent());
        assertEquals("T1110.003", result.get().attackTechnique());
    }

    /** B-01: a LOGIN_ATTEMPT with no payload used to throw an NPE inside the engine thread. */
    @Test
    void loginWithoutPayloadIsIgnoredNotFatal() {
        AttackerProfile profile = new AttackerProfile("10.10.0.7");
        profile.add(login(null));
        for (String user : List.of("alice", "bob", "carol")) {
            profile.add(login(user + ":Winter2026!"));
        }
        var result = assertDoesNotThrow(() -> new PasswordSprayRule().classify(profile));
        assertTrue(result.isPresent(), "the valid attempts still count");
    }

    @Test
    void ignoresDistinctPasswords() {
        AttackerProfile profile = new AttackerProfile("10.10.0.6");
        profile.add(login("alice:a"));
        profile.add(login("bob:b"));
        profile.add(login("carol:c"));
        assertTrue(new PasswordSprayRule().classify(profile).isEmpty());
    }
}
