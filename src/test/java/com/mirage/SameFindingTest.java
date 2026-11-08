package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.Classification;
import com.mirage.common.CommandType;
import com.mirage.common.Honeytoken;
import com.mirage.common.ResponseCommand;
import com.mirage.common.ScopeGuard;
import com.mirage.common.Severity;
import com.mirage.console.AlertBus;
import com.mirage.console.AlertLedger;
import com.mirage.console.AutoResponse;
import com.mirage.console.CorrelationEngine;
import com.mirage.console.HoneytokenStore;
import com.mirage.console.Persistence;
import com.mirage.console.ResponsePolicy;
import com.mirage.console.detection.DecoyAccessRule;
import com.mirage.console.detection.DetectionRule;
import com.mirage.console.detection.HoneytokenUseRule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-04: two rules confirming the same finding (same ATT&CK technique, same severity, same
 * source) make one alert and one response, not two.
 */
class SameFindingTest {

    private static final String SRC = "10.10.0.5";
    private static final String TOKEN = "svc_backup:S3cr3tBackup!";

    private final BlockingQueue<AttackEvent> queue = new LinkedBlockingQueue<>();
    private final List<ResponseCommand> sent = new CopyOnWriteArrayList<>();
    private final AlertLedger ledger = new AlertLedger();

    private void run(List<DetectionRule> rules, List<AttackEvent> events) throws InterruptedException {
        CorrelationEngine engine = new CorrelationEngine(queue, rules, ledger, new AlertBus(),
                new ResponsePolicy(new ScopeGuard(List.of("10.10.0.0/24")), sent::add), Persistence.NONE);
        engine.setAutoResponse(new AutoResponse(true));
        Thread t = new Thread(engine, "engine-test");
        t.setDaemon(true);
        t.start();
        queue.addAll(events);
        long end = System.currentTimeMillis() + 5_000;
        while (!queue.isEmpty() && System.currentTimeMillis() < end) {
            Thread.sleep(10);
        }
        Thread.sleep(300);
        t.interrupt();
    }

    private static AttackEvent event(Action action, String payload) {
        return new AttackEvent(SRC, 4444, "http-login", action, payload, Instant.now());
    }

    private static DetectionRule fixed(String technique, Severity severity) {
        return p -> Optional.of(new Classification("finding " + technique + " " + severity, technique, severity));
    }

    @Test
    void aHoneytokenBreakInIsOneAlertAndOneBlock() throws Exception {
        HoneytokenStore store = new HoneytokenStore();
        store.plant(new Honeytoken("ht-1", TOKEN, "config/backup.cfg"));
        run(List.of(new HoneytokenUseRule(store), new DecoyAccessRule()),
                List.of(event(Action.LOGIN_ATTEMPT, TOKEN), event(Action.DECOY_ACCESS, TOKEN)));
        assertEquals(1, ledger.size(), "one CRITICAL T1078 finding");
        assertEquals(List.of(new ResponseCommand(CommandType.BLOCK, SRC)), sent);
    }

    @Test
    void differentTechniquesAreStillSeparateFindings() throws Exception {
        run(List.of(fixed("T1046", Severity.MEDIUM), fixed("T1110", Severity.HIGH)),
                List.of(event(Action.CONNECT, "")));
        assertEquals(2, ledger.size());
    }

    @Test
    void theSameTechniqueAtAHigherSeverityStillAlerts() throws Exception {
        run(List.of(fixed("T9999", Severity.MEDIUM), fixed("T9999", Severity.HIGH)),
                List.of(event(Action.CONNECT, "")));
        assertEquals(2, ledger.size(), "MEDIUM, then HIGH: the severity rose");
    }
}
