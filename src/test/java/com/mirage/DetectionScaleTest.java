package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.Alert;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerProfile;
import com.mirage.common.AttackerSnapshot;
import com.mirage.common.Honeytoken;
import com.mirage.common.ScopeGuard;
import com.mirage.console.AlertBus;
import com.mirage.console.AlertLedger;
import com.mirage.console.AttackerDirectory;
import com.mirage.console.AttackerDossier;
import com.mirage.console.AutoResponse;
import com.mirage.console.CommandSink;
import com.mirage.console.CorrelationEngine;
import com.mirage.console.HoneytokenStore;
import com.mirage.console.Persistence;
import com.mirage.console.ResponsePolicy;
import com.mirage.console.detection.BaitFileRule;
import com.mirage.console.detection.BruteForceRule;
import com.mirage.console.detection.DecoyAccessRule;
import com.mirage.console.detection.DetectionRule;
import com.mirage.console.detection.HoneytokenUseRule;
import com.mirage.console.detection.LateralMovementRule;
import com.mirage.console.detection.PasswordSprayRule;
import com.mirage.console.detection.PortScanRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-02: every event made every rule re-scan (and the dashboard snapshot re-copy) the source's
 * whole lifetime history, so the cost per event grew with the history: 12 000 events from one
 * source delayed another source's CRITICAL alert from about 0.12 s to 10 s.
 */
class DetectionScaleTest {

    private static final String TOKEN = "svc_backup:S3cr3tBackup!";
    private static final int NOISE = 15_000;

    private Thread engineThread;

    @AfterEach
    void stop() {
        if (engineThread != null) {
            engineThread.interrupt();
        }
    }

    private static List<DetectionRule> realRules() {
        HoneytokenStore honeytokens = new HoneytokenStore();
        honeytokens.plant(new Honeytoken("ht-1", TOKEN, "config/backup.cfg"));
        return List.of(new PortScanRule(), new BruteForceRule(), new PasswordSprayRule(),
                new HoneytokenUseRule(honeytokens), new DecoyAccessRule(), new BaitFileRule(),
                new LateralMovementRule());
    }

    private static AttackEvent event(String ip, String decoy, Action action, String payload) {
        return new AttackEvent(ip, 4000, decoy, action, payload, Instant.now());
    }

    /**
     * The history kept per source is bounded, yet every finding still covers the whole
     * lifetime: each one is triggered only by events long gone from that history.
     */
    @Test
    void historyIsBoundedButEveryRuleStillSeesTheWholeLifetime() {
        String ip = "10.10.0.50";
        AttackerProfile profile = new AttackerProfile(ip);
        for (String decoy : List.of("http-login", "ftp", "telnet")) {
            profile.add(event(ip, decoy, Action.CONNECT, "").withSensor("sensor-a"));
        }
        profile.add(event(ip, "ftp", Action.CONNECT, "").withSensor("sensor-b"));
        for (String user : List.of("alice", "bob", "carol", "dave")) { // + the token = 5 logins
            profile.add(event(ip, "ftp", Action.LOGIN_ATTEMPT, user + ":Winter2026!"));
        }
        profile.add(event(ip, "http-login", Action.LOGIN_ATTEMPT, TOKEN));
        profile.add(event(ip, "http-login", Action.DECOY_ACCESS, TOKEN));
        profile.add(event(ip, "bait-file", Action.FILE_CHANGE, "ENTRY_MODIFY vpn.key"));
        int early = 11;
        for (int i = 0; i < NOISE; i++) {
            profile.add(event(ip, "telnet", Action.CONNECT, ""));
        }

        assertTrue(profile.events().size() <= AttackerProfile.HISTORY,
                "history kept: " + profile.events().size());
        assertEquals(early + NOISE, profile.eventCount(), "lifetime total");
        for (DetectionRule rule : realRules()) {
            assertTrue(rule.classify(profile).isPresent(),
                    rule.getClass().getSimpleName() + " must still see the early events");
        }

        AttackerDirectory directory = new AttackerDirectory();
        directory.update(profile);
        AttackerSnapshot snapshot = directory.snapshot(ip);
        assertTrue(snapshot.events().size() <= AttackerProfile.HISTORY, "the snapshot copies only the history");
        assertEquals(early + NOISE, snapshot.totalEvents());
        AttackerDossier dossier = AttackerDossier.from(snapshot);
        assertEquals(early + NOISE, dossier.eventCount(), "the dossier reports the lifetime total");
    }

    /** The end-to-end symptom: a noisy source must not hold up another source's CRITICAL alert. */
    @Test
    void oneNoisySourceDoesNotDelayAnotherSourcesAlert() throws InterruptedException {
        BlockingQueue<AttackEvent> queue = new LinkedBlockingQueue<>();
        BlockingQueue<Alert> alerts = new LinkedBlockingQueue<>();
        AlertBus bus = new AlertBus();
        bus.subscribe(alerts::add);
        CorrelationEngine engine = new CorrelationEngine(queue, realRules(), new AlertLedger(), bus,
                new ResponsePolicy(new ScopeGuard(List.of("10.10.0.0/24")), CommandSink.NONE), Persistence.NONE);
        engine.setAutoResponse(new AutoResponse(false));
        for (int i = 0; i < NOISE; i++) {
            queue.add(event("10.10.0.50", "ftp", Action.LOGIN_ATTEMPT, "admin:pw" + i));
        }
        queue.add(event("10.10.0.61", "http-login", Action.DECOY_ACCESS, TOKEN));

        long start = System.nanoTime();
        engineThread = new Thread(engine, "engine-scale-test");
        engineThread.setDaemon(true);
        engineThread.start();

        Alert other = null;
        long deadline = System.currentTimeMillis() + 5000;
        while (other == null && System.currentTimeMillis() < deadline) {
            Alert a = alerts.poll(100, TimeUnit.MILLISECONDS);
            if (a != null && a.sourceIp().equals("10.10.0.61")) {
                other = a;
            }
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertNotNull(other, "10.10.0.61's CRITICAL alert took over 5 s behind " + NOISE
                + " events from 10.10.0.50 (queue left: " + queue.size() + ")");
        System.out.println("[B-02] alert for the second source after " + NOISE + " noisy events: " + ms + " ms");
    }
}
