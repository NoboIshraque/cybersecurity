package com.mirage.console;

import com.mirage.common.AttackEvent;
import com.mirage.common.ScopeGuard;
import com.mirage.console.detection.BruteForceRule;
import com.mirage.console.detection.DetectionRule;
import com.mirage.console.detection.HoneytokenUseRule;
import com.mirage.console.detection.PortScanRule;

import javax.net.ServerSocketFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Entry point for the blue-team Console. Wires the pipeline and the Sensor server. */
public final class ConsoleMain {

    private static final int PORT = 8443;

    public static void main(String[] args) throws Exception {
        System.out.println("Mirage Console starting...");

        // 127.0.0.1 is allowed so the system can be demonstrated on one machine.
        ScopeGuard guard = new ScopeGuard(List.of("10.10.0.0/24", "127.0.0.1/32"));
        AlertLedger ledger = new AlertLedger();
        AlertBus bus = new AlertBus();
        bus.subscribe(alert -> System.out.println("[alert] " + alert.canonical()));

        ResponsePolicy policy = new ResponsePolicy(guard);
        List<DetectionRule> rules = List.of(
                new PortScanRule(), new BruteForceRule(), new HoneytokenUseRule());

        BlockingQueue<AttackEvent> events = new LinkedBlockingQueue<>();
        CorrelationEngine engine = new CorrelationEngine(events, rules, ledger, bus, policy);
        Thread engineThread = new Thread(engine, "correlation-engine");
        engineThread.setDaemon(true);
        engineThread.start();

        SensorRegistry registry = new SensorRegistry();
        ConsoleServer server = new ConsoleServer(ServerSocketFactory.getDefault(), events, registry);
        int port = server.start(PORT);
        System.out.println("Console listening for Sensors on port " + port + " (plain; TLS in production).");

        ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor();
        sweeper.scheduleAtFixedRate(() -> {
            for (String dead : registry.deadSensors(Duration.ofSeconds(30), Instant.now())) {
                System.out.println("[warn] sensor silent (possible tamper): " + dead);
            }
        }, 15, 15, TimeUnit.SECONDS);

        System.out.println("Correlation engine running with " + rules.size() + " rules. Ctrl+C to stop.");
        Thread.currentThread().join();
    }
}
