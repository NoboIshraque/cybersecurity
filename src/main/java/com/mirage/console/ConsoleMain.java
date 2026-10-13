package com.mirage.console;

import com.mirage.common.AttackEvent;
import com.mirage.common.ScopeGuard;
import com.mirage.console.detection.BruteForceRule;
import com.mirage.console.detection.DetectionRule;
import com.mirage.console.detection.HoneytokenUseRule;
import com.mirage.console.detection.PortScanRule;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** Entry point for the blue-team Console. Wires the pipeline together. */
public final class ConsoleMain {

    public static void main(String[] args) {
        System.out.println("Mirage Console starting...");

        ScopeGuard guard = new ScopeGuard(List.of("10.10.0.0/24"));
        AlertLedger ledger = new AlertLedger();
        AlertBus bus = new AlertBus();
        bus.subscribe(alert -> System.out.println("[alert] " + alert.canonical()));

        ResponsePolicy policy = new ResponsePolicy(guard);
        List<DetectionRule> rules = List.of(
                new PortScanRule(), new BruteForceRule(), new HoneytokenUseRule());

        BlockingQueue<AttackEvent> events = new LinkedBlockingQueue<>();
        CorrelationEngine engine = new CorrelationEngine(events, rules, ledger, bus, policy);

        Thread t = new Thread(engine, "correlation-engine");
        t.setDaemon(true);
        t.start();

        System.out.println("Correlation engine running with " + rules.size() + " detection rules.");
        System.out.println("TODO: start the TLS Sensor server and the Swing dashboard. See HANDOVER.md.");
    }
}
