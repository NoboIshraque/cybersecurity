package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.Alert;
import com.mirage.common.AttackEvent;
import com.mirage.common.ResponseCommand;
import com.mirage.common.ScopeGuard;
import com.mirage.console.AlertBus;
import com.mirage.console.AlertLedger;
import com.mirage.console.AutoResponse;
import com.mirage.console.CommandSink;
import com.mirage.console.CorrelationEngine;
import com.mirage.console.Persistence;
import com.mirage.console.ResponsePolicy;
import com.mirage.console.detection.DecoyAccessRule;
import com.mirage.console.detection.DetectionRule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** With auto-response off, alerts are still recorded and surfaced, but nothing is dispatched. */
class AutoResponseSwitchTest {

    private BlockingQueue<AttackEvent> queue;
    private BlockingQueue<Alert> alerts;
    private BlockingQueue<ResponseCommand> commands;
    private AutoResponse auto;

    private void wire(boolean autoEnabled) {
        queue = new LinkedBlockingQueue<>();
        alerts = new LinkedBlockingQueue<>();
        commands = new LinkedBlockingQueue<>();
        AlertBus bus = new AlertBus();
        bus.subscribe(alerts::add);
        CommandSink sink = commands::add;
        ScopeGuard guard = new ScopeGuard(List.of("10.10.0.0/24"));
        ResponsePolicy policy = new ResponsePolicy(guard, sink);
        List<DetectionRule> rules = List.of(new DecoyAccessRule());
        auto = new AutoResponse(autoEnabled);
        CorrelationEngine engine = new CorrelationEngine(queue, rules,
                new AlertLedger(), bus, policy, Persistence.NONE);
        engine.setAutoResponse(auto);
        Thread t = new Thread(engine, "engine-test");
        t.setDaemon(true);
        t.start();
    }

    private void feedDecoyAccess() {
        queue.add(new AttackEvent("10.10.0.5", 5000, "http-login", Action.DECOY_ACCESS,
                "svc_backup:S3cr3tBackup!", Instant.now()));
    }

    @Test
    void offRecordsTheAlertButDispatchesNothing() throws Exception {
        wire(false);
        feedDecoyAccess();
        // give the engine a moment to process before asserting absence
        Thread.sleep(50);
        assertNotNull(alerts.poll(2, TimeUnit.SECONDS), "alert must still be raised");
        assertNull(commands.poll(500, TimeUnit.MILLISECONDS), "no command should be dispatched when off");
    }

    @Test
    void onDispatchesTheAutomaticResponse() throws Exception {
        wire(true);
        feedDecoyAccess();
        assertNotNull(alerts.poll(2, TimeUnit.SECONDS), "alert must be raised");
        assertNotNull(commands.poll(2, TimeUnit.SECONDS), "a command should be dispatched when on");
    }

    @Test
    void theSwitchCanBeFlippedLive() {
        wire(false);
        assertFalse(auto.isEnabled());
        auto.setEnabled(true);
        assertTrue(auto.isEnabled());
    }
}
