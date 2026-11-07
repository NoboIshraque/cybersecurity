package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.Alert;
import com.mirage.common.AttackEvent;
import com.mirage.common.Classification;
import com.mirage.common.ScopeGuard;
import com.mirage.common.Severity;
import com.mirage.console.AlertBus;
import com.mirage.console.AlertLedger;
import com.mirage.console.CommandSink;
import com.mirage.console.CorrelationEngine;
import com.mirage.console.Persistence;
import com.mirage.console.ResponsePolicy;
import com.mirage.console.detection.DetectionRule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A rule that keeps matching a source raises one alert, not one per event; it
 * raises again only when its severity for that source rises, or after the
 * operator unblocks the source.
 */
class AlertDedupTest {

    /** MEDIUM from the first event, HIGH from the third. */
    private static final DetectionRule GROWING = profile -> Optional.of(new Classification(
            "test finding", "T0000", profile.events().size() >= 3 ? Severity.HIGH : Severity.MEDIUM));

    private final BlockingQueue<AttackEvent> queue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Alert> alerts = new LinkedBlockingQueue<>();
    private final AlertLedger ledger = new AlertLedger();
    private CorrelationEngine engine;

    private void wire() {
        AlertBus bus = new AlertBus();
        bus.subscribe(alerts::add);
        engine = new CorrelationEngine(queue, List.of(GROWING), ledger, bus,
                new ResponsePolicy(new ScopeGuard(List.of("10.10.0.0/24")), CommandSink.NONE), Persistence.NONE);
        engine.setAutoResponse(new com.mirage.console.AutoResponse(false));
        Thread t = new Thread(engine, "engine-test");
        t.setDaemon(true);
        t.start();
    }

    private void feed(String ip, int n) {
        for (int i = 0; i < n; i++) {
            queue.add(new AttackEvent(ip, 5000 + i, "http-login", Action.LOGIN_ATTEMPT, "a:b", Instant.now()));
        }
    }

    private Severity next() throws InterruptedException {
        Alert a = alerts.poll(2, TimeUnit.SECONDS);
        assertNotNull(a, "expected an alert");
        return a.classification().severity();
    }

    private void assertNoMore() throws InterruptedException {
        assertNull(alerts.poll(400, TimeUnit.MILLISECONDS), "no duplicate alert expected");
    }

    @Test
    void oneAlertPerFindingThenOnlyOnEscalation() throws Exception {
        wire();
        feed("10.10.0.5", 6); // matches on all six events
        assertEquals(Severity.MEDIUM, next());
        assertEquals(Severity.HIGH, next(), "rising severity is a new finding");
        assertNoMore();
        assertEquals(2, ledger.size(), "the ledger records each finding once, not once per event");
    }

    @Test
    void eachSourceIsTrackedSeparately() throws Exception {
        wire();
        feed("10.10.0.5", 1);
        feed("10.10.0.6", 1);
        assertEquals(Severity.MEDIUM, next());
        assertEquals(Severity.MEDIUM, next());
        assertNoMore();
    }

    @Test
    void unblockingASourceLetsItAlertAgain() throws Exception {
        wire();
        feed("10.10.0.5", 3);
        next();
        next();
        assertNoMore();
        engine.resetAlerts("10.10.0.5");
        feed("10.10.0.5", 1);
        // B-03: a clean slate judges only the new activity, so one event is MEDIUM again,
        // not the old HIGH raised a second time
        assertEquals(Severity.MEDIUM, next(), "after a reset only new activity is judged");
        assertNoMore();
        feed("10.10.0.5", 2);
        assertEquals(Severity.HIGH, next(), "and it escalates again on its own new activity");
        assertNoMore();
    }
}
