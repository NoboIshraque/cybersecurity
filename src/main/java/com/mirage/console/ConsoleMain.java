package com.mirage.console;

import com.mirage.common.AttackEvent;
import com.mirage.common.Honeytoken;
import com.mirage.common.ScopeGuard;
import com.mirage.console.db.Database;
import com.mirage.console.detection.BaitFileRule;
import com.mirage.console.detection.BruteForceRule;
import com.mirage.console.detection.DetectionRule;
import com.mirage.console.detection.HoneytokenUseRule;
import com.mirage.console.detection.PasswordSprayRule;
import com.mirage.console.detection.PortScanRule;
import com.mirage.console.ui.Dashboard;

import javax.net.ServerSocketFactory;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Entry point for the blue-team Console. Wires the pipeline, persistence, server and dashboard. */
public final class ConsoleMain {

    private static final int PORT = 8443;

    public static void main(String[] args) throws Exception {
        System.out.println("Mirage Console starting...");

        // 127.0.0.1 is allowed so the system can be demonstrated on one machine.
        ScopeGuard guard = new ScopeGuard(List.of("10.10.0.0/24", "127.0.0.1/32"));
        AlertLedger ledger = new AlertLedger(new KeyPairSigner());
        AlertBus bus = new AlertBus();
        bus.subscribe(alert -> System.out.println("[alert] " + alert.canonical()));

        Database database = new Database("jdbc:h2:file:./data/mirage");
        Persistence persistence = new H2Persistence(database);
        System.out.println("Persistence ready (H2 at ./data/mirage).");

        HoneytokenStore honeytokens = new HoneytokenStore();
        honeytokens.plant(new Honeytoken("ht-1", "svc_backup:S3cr3tBackup!", "config/backup.cfg"));

        BlockingQueue<AttackEvent> events = new LinkedBlockingQueue<>();
        SensorRegistry registry = new SensorRegistry();
        ConsoleServer server = new ConsoleServer(ServerSocketFactory.getDefault(), events, registry);

        // The server is the CommandSink: active-defence responses broadcast to the Sensors.
        ResponsePolicy policy = new ResponsePolicy(guard, server);
        List<DetectionRule> rules = List.of(
                new PortScanRule(),
                new BruteForceRule(),
                new PasswordSprayRule(),
                new HoneytokenUseRule(honeytokens),
                new BaitFileRule());

        CorrelationEngine engine = new CorrelationEngine(events, rules, ledger, bus, policy, persistence);
        Thread engineThread = new Thread(engine, "correlation-engine");
        engineThread.setDaemon(true);
        engineThread.start();

        int port = server.start(PORT);
        System.out.println("Console listening for Sensors on port " + port + " (plain; TLS in production).");

        ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor();
        sweeper.scheduleAtFixedRate(() -> {
            for (String dead : registry.deadSensors(Duration.ofSeconds(30), Instant.now())) {
                System.out.println("[warn] sensor silent (possible tamper): " + dead);
            }
        }, 15, 15, TimeUnit.SECONDS);

        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Headless: dashboard not shown (console logging only).");
        } else {
            SwingUtilities.invokeLater(() -> {
                Dashboard dashboard = new Dashboard(ledger, server::connectionCount);
                bus.subscribe(dashboard::onAlert);
                dashboard.setVisible(true);
            });
            System.out.println("Dashboard opening.");
        }

        System.out.println("Correlation engine running with " + rules.size() + " rules. Ctrl+C to stop.");
        Thread.currentThread().join();
    }
}
