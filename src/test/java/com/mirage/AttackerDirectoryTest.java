package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerProfile;
import com.mirage.common.AttackerSnapshot;
import com.mirage.console.AttackerDirectory;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AttackerDirectoryTest {

    private static AttackEvent event(String ip, String payload) {
        return new AttackEvent(ip, 1234, "http-login", Action.LOGIN_ATTEMPT, payload, Instant.now());
    }

    @Test
    void publishesAnImmutableSnapshotThatTracksTheProfile() {
        AttackerDirectory dir = new AttackerDirectory();
        assertNull(dir.snapshot("10.10.0.7"));

        AttackerProfile profile = new AttackerProfile("10.10.0.7");
        profile.add(event("10.10.0.7", "admin:123456"));
        dir.update(profile);

        AttackerSnapshot first = dir.snapshot("10.10.0.7");
        assertEquals(1, first.events().size());
        assertTrue(dir.sources().contains("10.10.0.7"));

        // the snapshot is an immutable copy
        assertThrows(UnsupportedOperationException.class,
                () -> first.events().add(event("10.10.0.7", "x:y")));

        // a later profile change does not mutate the earlier snapshot
        profile.add(event("10.10.0.7", "admin:password"));
        assertEquals(1, first.events().size());

        // ...but a fresh update publishes the new state
        dir.update(profile);
        assertEquals(2, dir.snapshot("10.10.0.7").events().size());
    }
}
