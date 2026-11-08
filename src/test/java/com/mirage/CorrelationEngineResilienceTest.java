package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.Alert;
import com.mirage.common.AttackEvent;
import com.mirage.common.Classification;
import com.mirage.common.LedgerEntry;
import com.mirage.common.ScopeGuard;
import com.mirage.common.Severity;
import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;
import com.mirage.console.AlertBus;
import com.mirage.console.AlertLedger;
import com.mirage.console.AutoResponse;
import com.mirage.console.CommandSink;
import com.mirage.console.CorrelationEngine;
import com.mirage.console.Persistence;
import com.mirage.console.ResponsePolicy;
import com.mirage.console.detection.DetectionRule;
import com.mirage.console.detection.PasswordSprayRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-01: one bad event used to end the engine's only consumer thread, silently, so nothing
 * after it was classified, recorded or answered. Each case here feeds a bad event, then a
 * good one, on the real engine thread, and expects the good one to still raise its alert.
 */
class CorrelationEngineResilienceTest {

    /** Alerts on any DECOY_ACCESS: the "good" event each case uses to prove the engine is alive. */
    private static final DetectionRule ACCESS = profile -> profile.events().stream()
            .anyMatch(e -> e.action() == Action.DECOY_ACCESS)
            ? Optional.of(new Classification("decoy access", "T0001", Severity.HIGH))
            : Optional.empty();

    private final BlockingQueue<AttackEvent> queue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Alert> alerts = new LinkedBlockingQueue<>();
    private final List<TraceEvent> traces = new CopyOnWriteArrayList<>();
    private final AlertLedger ledger = new AlertLedger();
    private Thread thread;

    private void start(List<DetectionRule> rules, Persistence persistence) {
        AlertBus bus = new AlertBus();
        bus.subscribe(alerts::add);
        CorrelationEngine engine = new CorrelationEngine(queue, rules, ledger, bus,
                new ResponsePolicy(new ScopeGuard(List.of("10.10.0.0/24")), CommandSink.NONE), persistence);
        engine.setAutoResponse(new AutoResponse(false));
        engine.setTrace(traces::add);
        thread = new Thread(engine, "engine-test");
        thread.setDaemon(true);
        thread.start();
    }

    @AfterEach
    void stop() {
        if (thread != null) {
            thread.interrupt();
        }
    }

    private static AttackEvent event(String ip, Action action, String payload) {
        return new AttackEvent(ip, 4000, "ftp", action, payload, Instant.now());
    }

    private void assertStillAlive() throws InterruptedException {
        queue.add(event("10.10.0.9", Action.DECOY_ACCESS, "admin:admin"));
        Alert alert = alerts.poll(3, TimeUnit.SECONDS);
        assertNotNull(alert, "the event after the bad one must still be classified");
        assertEquals("10.10.0.9", alert.sourceIp());
        assertTrue(thread.isAlive(), "engine thread still running");
    }

    private boolean tracedBad() {
        return traces.stream().anyMatch(t -> t.level() == TraceLevel.BAD && t.text().contains("dropped"));
    }

    @Test
    void survivesAThrowingRule() throws InterruptedException {
        DetectionRule broken = profile -> {
            if (profile.sourceIp().equals("10.10.0.66")) {
                throw new IllegalStateException("rule bug");
            }
            return Optional.empty();
        };
        start(List.of(broken, ACCESS), Persistence.NONE);
        queue.add(event("10.10.0.66", Action.CONNECT, ""));
        assertStillAlive();
        assertTrue(tracedBad(), "the failure is shown in the console shell, not just lost");
    }

    @Test
    void survivesALoginWithNoPayload() throws InterruptedException {
        start(List.of(new PasswordSprayRule(), ACCESS), Persistence.NONE);
        queue.add(event("10.10.0.5", Action.LOGIN_ATTEMPT, null));
        assertStillAlive();
    }

    @Test
    void survivesAnEventWithNoSource() throws InterruptedException {
        start(List.of(ACCESS), Persistence.NONE);
        queue.add(event(null, Action.CONNECT, ""));
        assertStillAlive();
        assertTrue(tracedBad());
    }

    /** e.g. H2 "Value too long for column SRC_IP": the event is not stored, but still classified. */
    @Test
    void aDatabaseErrorDoesNotStopDetection() throws InterruptedException {
        List<AttackEvent> saved = new ArrayList<>();
        Persistence flaky = new Persistence() {
            @Override public void saveEvent(AttackEvent e) {
                if (e.sourceIp().equals("10.10.0.9")) {
                    throw new IllegalStateException("Value too long for column SRC_IP");
                }
                saved.add(e);
            }
            @Override public void saveAlert(LedgerEntry entry) {
                throw new IllegalStateException("disk full");
            }
        };
        start(List.of(ACCESS), flaky);
        assertStillAlive(); // the DECOY_ACCESS from 10.10.0.9 fails to save, yet alerts
        assertEquals(1, ledger.size(), "the alert is in the in-memory ledger even though H2 failed");
        assertTrue(traces.stream().anyMatch(t -> t.level() == TraceLevel.WARN && t.text().contains("H2")),
                "the save failure is visible in the shell");
    }
}
