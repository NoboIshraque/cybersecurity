package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerProfile;
import com.mirage.common.LabConfig;
import com.mirage.console.detection.BruteForceRule;
import com.mirage.console.detection.PasswordSprayRule;
import com.mirage.console.detection.PortScanRule;
import com.mirage.sensor.FakeHttpLoginDecoy;
import com.mirage.specter.AttackStrategy;
import com.mirage.specter.Campaign;
import com.mirage.specter.Campaigns;
import com.mirage.specter.Target;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-28: the counting rules look at a time window, not the source's whole lifetime, and their
 * thresholds leave headroom below what Specter's demo campaigns send.
 */
class RuleWindowTest {

    private static final Instant T0 = Instant.parse("2026-11-08T10:00:00Z");

    private static AttackEvent at(long seconds, String decoy, Action action, String payload) {
        return new AttackEvent("10.10.0.5", 4444, decoy, action, payload, T0.plusSeconds(seconds));
    }

    private static AttackEvent login(long seconds, String credentials) {
        return at(seconds, "http-login", Action.LOGIN_ATTEMPT, credentials);
    }

    private static boolean brute(AttackerProfile p) {
        return new BruteForceRule().classify(p).isPresent();
    }

    private static boolean spray(AttackerProfile p) {
        return new PasswordSprayRule().classify(p).isPresent();
    }

    private static boolean scan(AttackerProfile p) {
        return new PortScanRule().classify(p).isPresent();
    }

    // ---- brute force: 5 different passwords on one account within 60 s -----------------

    @Test
    void fivePasswordsOnOneAccountWithinAMinuteIsBruteForce() {
        AttackerProfile p = new AttackerProfile("10.10.0.5");
        for (int i = 0; i < 4; i++) {
            p.add(login(i * 10, "admin:guess" + i));
        }
        assertFalse(brute(p), "4 is below the threshold");
        p.add(login(59, "admin:guess4"));
        assertTrue(brute(p), "the 5th, 59 s after the first, is inside the window");
    }

    @Test
    void theFirstAttemptFallsOutOfTheWindowAfterSixtySeconds() {
        AttackerProfile p = new AttackerProfile("10.10.0.5");
        for (int i = 0; i < 4; i++) {
            p.add(login(i * 10, "admin:guess" + i));
        }
        p.add(login(61, "admin:guess4"));
        assertFalse(brute(p), "61 s after the first: only 4 are left in the window");
    }

    @Test
    void attemptsSpreadOverAnHourAreNotBruteForce() {
        AttackerProfile p = new AttackerProfile("10.10.0.5");
        for (int i = 0; i < 10; i++) {
            p.add(login(i * 600L, "admin:guess" + i)); // one every 10 minutes
        }
        assertFalse(brute(p));
    }

    @Test
    void aSprayFollowedByATypoIsNotBruteForce() {
        AttackerProfile p = new AttackerProfile("10.10.0.5");
        int s = 0;
        for (String user : List.of("admin", "root", "user", "test")) {
            p.add(login(s++, user + ":Password123!"));
        }
        p.add(login(10, "admin:Pasword123!"));
        assertTrue(spray(p));
        assertFalse(brute(p), "five logins, but at most two passwords on any one account");
    }

    @Test
    void retryingTheSamePasswordIsNotGuessing() {
        AttackerProfile p = new AttackerProfile("10.10.0.5");
        for (int i = 0; i < 6; i++) {
            p.add(login(i, "admin:wrong"));
        }
        assertFalse(brute(p));
    }

    // ---- password spray: one password, 3 accounts, within 10 minutes ------------------

    @Test
    void aSprayIsAllowedTenMinutesButNotMore() {
        AttackerProfile slow = new AttackerProfile("10.10.0.5");
        slow.add(login(0, "alice:Winter2026!"));
        slow.add(login(300, "bob:Winter2026!"));
        slow.add(login(600, "carol:Winter2026!"));
        assertTrue(spray(slow), "3 accounts within exactly 10 minutes");

        AttackerProfile slower = new AttackerProfile("10.10.0.6");
        slower.add(login(0, "alice:Winter2026!"));
        slower.add(login(301, "bob:Winter2026!"));
        slower.add(login(602, "carol:Winter2026!"));
        assertFalse(spray(slower), "alice fell out of the window before carol");
    }

    // ---- port scan: 2 decoy services within 60 s ------------------------------------------

    @Test
    void twoServicesWithinAMinuteIsAScan() {
        AttackerProfile p = new AttackerProfile("10.10.0.5");
        p.add(at(0, "http-login", Action.CONNECT, ""));
        assertFalse(scan(p));
        p.add(at(30, "ftp", Action.CONNECT, ""));
        assertTrue(scan(p), "a scan is seen even if one of the three decoys is down");
    }

    @Test
    void twoServicesHoursApartAreNotAScan() {
        AttackerProfile p = new AttackerProfile("10.10.0.5");
        p.add(at(0, "http-login", Action.CONNECT, ""));
        p.add(at(7200, "ftp", Action.CONNECT, ""));
        assertFalse(scan(p));
    }

    // ---- headroom: Specter's demo brute force survives a lost attempt ----------------------

    @Test
    void theDemoBruteForceIsDetectedEvenIfAnyOneAttemptIsLost() throws Exception {
        List<AttackEvent> seen = new CopyOnWriteArrayList<>();
        FakeHttpLoginDecoy decoy = new FakeHttpLoginDecoy(0);
        decoy.start(seen::add);
        try {
            Campaign brute = Campaigns.forChoice("Brute force", LabConfig.from(Map.of()));
            for (AttackStrategy s : brute.strategies()) {
                s.run(new Target("127.0.0.1", decoy.boundPort()), (level, text) -> { });
            }
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (seen.stream().noneMatch(e -> e.action() == Action.LOGIN_ATTEMPT) && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            Thread.sleep(200);
        } finally {
            decoy.stop();
        }
        List<AttackEvent> logins = seen.stream().filter(e -> e.action() == Action.LOGIN_ATTEMPT).toList();
        assertTrue(logins.size() >= 7, "the demo sends a margin over the threshold of 5: " + logins.size());
        for (int lost = 0; lost < logins.size(); lost++) {
            AttackerProfile p = new AttackerProfile("127.0.0.1");
            for (int i = 0; i < logins.size(); i++) {
                if (i != lost) {
                    p.add(logins.get(i));
                }
            }
            assertTrue(brute(p), "still detected without attempt #" + lost);
        }
    }
}
