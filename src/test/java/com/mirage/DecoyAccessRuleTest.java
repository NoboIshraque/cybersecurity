package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerProfile;
import com.mirage.console.detection.DecoyAccessRule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DecoyAccessRuleTest {

    @Test
    void firesCriticalWhenTheIntruderIsInside() {
        AttackerProfile profile = new AttackerProfile("10.10.0.9");
        profile.add(new AttackEvent("10.10.0.9", 5000, "http-login", Action.DECOY_ACCESS,
                "svc_backup:S3cr3tBackup!", Instant.now()));
        var result = new DecoyAccessRule().classify(profile);
        assertTrue(result.isPresent());
        assertEquals("T1078", result.get().attackTechnique());
    }

    @Test
    void silentWithoutADecoyAccess() {
        AttackerProfile profile = new AttackerProfile("10.10.0.9");
        profile.add(new AttackEvent("10.10.0.9", 5000, "http-login", Action.LOGIN_ATTEMPT,
                "admin:wrong", Instant.now()));
        assertTrue(new DecoyAccessRule().classify(profile).isEmpty());
    }
}
