package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerSnapshot;
import com.mirage.console.AttackerDossier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttackerDossierTest {

    @Test
    void derivesServicesCredentialsAndArtifactsFromEvents() {
        Instant t0 = Instant.parse("2026-10-14T00:00:00Z");
        AttackerSnapshot snap = new AttackerSnapshot("10.10.0.7", List.of(
                new AttackEvent("10.10.0.7", 5000, "scan", Action.PORT_PROBE, "8081", t0),
                new AttackEvent("10.10.0.7", 5001, "http-login", Action.LOGIN_ATTEMPT, "admin:123456", t0.plusSeconds(1)),
                new AttackEvent("10.10.0.7", 5002, "http-login", Action.LOGIN_ATTEMPT, "admin:password", t0.plusSeconds(2)),
                new AttackEvent("10.10.0.7", 5003, "ftp", Action.LOGIN_ATTEMPT, "root:123456", t0.plusSeconds(3)),
                new AttackEvent("10.10.0.7", 5004, "http-login", Action.HONEYTOKEN_USE, "svc_backup:S3cr3tBackup!", t0.plusSeconds(4)),
                new AttackEvent("10.10.0.7", 0, "bait-file", Action.FILE_CHANGE, "config/backup.cfg", t0.plusSeconds(5))));

        AttackerDossier d = AttackerDossier.from(snap);

        assertEquals("10.10.0.7", d.sourceIp());
        assertEquals(6, d.eventCount());
        assertEquals(1, d.portProbes());
        assertEquals(List.of("scan", "http-login", "ftp", "bait-file"), d.servicesProbed());
        assertEquals(List.of("admin:123456", "admin:password", "root:123456"), d.credentialsTried());
        // distinct passwords (part after the last ':'), 123456 appears twice -> once
        assertEquals(List.of("123456", "password"), d.passwordsTried());
        assertEquals(List.of("svc_backup:S3cr3tBackup!"), d.honeytokensUsed());
        assertEquals(List.of("config/backup.cfg"), d.baitFilesTouched());
        assertEquals(t0, d.firstSeen());
        assertEquals(t0.plusSeconds(5), d.lastSeen());
    }

    @Test
    void emptyWhenNoEvents() {
        AttackerDossier d = AttackerDossier.from(new AttackerSnapshot("10.10.0.1", List.of()));
        assertEquals(0, d.eventCount());
        assertTrue(d.servicesProbed().isEmpty());
        assertTrue(d.passwordsTried().isEmpty());
        assertNull(d.firstSeen());
    }
}
