package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerProfile;
import com.mirage.common.Classification;
import com.mirage.common.Severity;
import com.mirage.console.detection.BaitFileRule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BaitFileRuleTest {

    @Test
    void flagsBaitFileChangeAsCritical() {
        AttackerProfile profile = new AttackerProfile("local-filesystem");
        profile.add(new AttackEvent("local-filesystem", 0, "bait-file",
                Action.FILE_CHANGE, "ENTRY_MODIFY passwords.xlsx", Instant.now()));

        Optional<Classification> result = new BaitFileRule().classify(profile);
        assertTrue(result.isPresent());
        assertEquals(Severity.CRITICAL, result.get().severity());
    }
}
