package com.mirage.console;

import com.mirage.common.AttackEvent;
import com.mirage.common.Honeytoken;
import com.mirage.common.LabConfig;
import com.mirage.common.ScopeGuard;
import com.mirage.common.trace.TraceBus;
import com.mirage.common.TlsFactories;
import com.mirage.console.db.Database;
import com.mirage.console.detection.BaitFileRule;
import com.mirage.console.detection.BruteForceRule;
import com.mirage.console.detection.DecoyAccessRule;
import com.mirage.console.detection.DetectionRule;
import com.mirage.console.detection.HoneytokenUseRule;
import com.mirage.console.detection.LateralMovementRule;
import com.mirage.console.detection.PasswordSprayRule;
import com.mirage.console.detection.PortScanRule;
import com.mirage.console.ui.Dashboard;
import com.mirage.console.ui.LiveOptions;
import com.mirage.specter.Attacker;

import javax.net.ServerSocketFactory;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.net.BindException;
import java.nio.file.Path;
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

    public static void main(String[] args) throws Exception {
        try {
            start(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage()); // an unsafe lab range: refuse, as before
            return;
        }
        Thread.currentThread().join();
    }

    /**
     * Wire and start the Console; returns the port it is listening on. Throws if it can't start
     * (B-10): {@link IllegalArgumentException} for an unsafe lab range, {@link BindException}
     * when the Console port is already taken, so a caller (the {@code defender} role) never
     * mistakes someone else's listener on that port for its own Console.
     */
    public static int start(String[] args) throws Exception {
        System.out.println("Mirage Console starting...");

        LabConfig config = LabConfig.load(args);
        System.out.println("Config: " + config.summary());
        ScopeGuard guard;
        try {
            guard = config.scopeGuard(); // validates the lab range (private/loopback, /16..32)
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Refusing to start: " + e.getMessage(), e);
        }
        // Only advertise an address inside the lab range (skips VirtualBox / VPN adapters): an
        // address outside it could never be attacked or defended, so it would only mislead.
        java.util.Optional<String> lanIp = LabConfig.primaryLanIp(guard::isAuthorised)
                .filter(guard::isAuthorised);
        lanIp.ifPresentOrElse(
                ip -> System.out.println("This host's LAN address: " + ip
                        + "  (on the attacker laptop set console.host=" + ip + ")"),
                () -> System.out.println("No LAN address inside the lab range " + config.labCidrs()
                        + "; single-machine demo on 127.0.0.1 (set lab.cidrs to your network for two laptops)."));
        AlertLedger ledger = new AlertLedger(new KeyPairSigner());
        AlertBus bus = new AlertBus();
        bus.subscribe(alert -> System.out.println("[alert] " + alert.canonical()));

        Database database = new Database("jdbc:h2:file:./data/mirage");
        Persistence persistence = new H2Persistence(database);
        System.out.println("Persistence ready (H2 at ./data/mirage).");

        HoneytokenStore honeytokens = new HoneytokenStore();
        honeytokens.plant(new Honeytoken("ht-1", "svc_backup:S3cr3tBackup!", "config/backup.cfg"));

        // The live process shells subscribe to this bus; nothing is printed until one attaches.
        TraceBus trace = new TraceBus();

        BlockingQueue<AttackEvent> events = new LinkedBlockingQueue<>();
        SensorRegistry registry = new SensorRegistry();
        ConsoleServer server = new ConsoleServer(serverSocketFactory(), events, registry);
        server.setTrace(trace);
        AttackerDirectory directory = new AttackerDirectory();

        // The server is the CommandSink: active-defence responses broadcast to the Sensors.
        ResponsePolicy policy = new ResponsePolicy(guard, server);
        ResponseController responses = new ResponseController(guard, server);
        responses.setTrace(trace);

        // Operator-triggered counter-offensive: mirror the attacker's own technique back,
        // through the same ScopeGuard, so it can only ever reach a source inside the lab range.
        Attacker counterAttacker = new Attacker(guard, line -> System.out.println("[counter] " + line));
        counterAttacker.setTrace(trace, "op");
        CounterOffensive counter = new CounterOffensive(
                counterAttacker, new MirrorCounterStrategy(), config.counterWatchPort());
        counter.setTrace(trace);
        List<DetectionRule> rules = List.of(
                new PortScanRule(),
                new BruteForceRule(),
                new PasswordSprayRule(),
                new HoneytokenUseRule(honeytokens),
                new DecoyAccessRule(),
                new BaitFileRule(),
                new LateralMovementRule());

        // Manual by default when an operator is watching the dashboard; automatic when headless,
        // because nobody is there to click. The dashboard checkbox flips this shared switch.
        boolean headless = GraphicsEnvironment.isHeadless();
        AutoResponse autoResponse = new AutoResponse(headless);
        System.out.println("Auto-response: " + (headless ? "ON (headless, no operator)"
                : "OFF - the operator responds from the dashboard / threat popups"));

        CorrelationEngine engine = new CorrelationEngine(events, rules, ledger, bus, policy, persistence, directory);
        engine.setTrace(trace);
        engine.setAutoResponse(autoResponse);
        responses.setOnUnblock(engine::resetAlerts); // an unblocked source starts with a clean slate
        Thread engineThread = new Thread(engine, "correlation-engine");
        engineThread.setDaemon(true);
        engineThread.start();

        int port;
        try {
            port = server.start(config.consolePort());
        } catch (BindException e) {
            BindException taken = new BindException("the Console can't listen on port " + config.consolePort()
                    + " (" + e.getMessage() + "): is another Console or defender already running?");
            taken.initCause(e);
            throw taken;
        }
        System.out.println("Console listening for Sensors on port " + port + ".");

        ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor();
        sweeper.scheduleAtFixedRate(() -> {
            for (String dead : registry.deadSensors(Duration.ofSeconds(30), Instant.now())) {
                System.out.println("[warn] sensor silent (possible tamper): " + dead);
            }
        }, 15, 15, TimeUnit.SECONDS);

        String consoleAddress = lanIp.orElse("127.0.0.1") + ":" + port;
        if (headless) {
            System.out.println("Headless: dashboard not shown (console logging only).");
        } else {
            LiveOptions live = new LiveOptions(trace, autoResponse, consoleAddress);
            SwingUtilities.invokeLater(() -> {
                // The dashboard subscribes the threat popups, operation shells and pipeline shell to the TraceBus.
                Dashboard dashboard = new Dashboard(ledger, server::connectionCount, directory, responses, counter, live);
                bus.subscribe(dashboard::onAlert);
                dashboard.setVisible(true);
            });
            System.out.println("Dashboard opening.");
        }

        System.out.println("Correlation engine running with " + rules.size() + " rules. Ctrl+C to stop.");
        return port;
    }

    /** TLS when a keystore is configured (-Dmirage.keystore=...), otherwise plain sockets. */
    private static ServerSocketFactory serverSocketFactory() {
        String keystore = System.getProperty("mirage.keystore");
        if (keystore != null) {
            char[] password = System.getProperty("mirage.keystore.password", "changeit").toCharArray();
            try {
                ServerSocketFactory factory = TlsFactories.serverFactory(Path.of(keystore), password);
                System.out.println("TLS enabled (keystore " + keystore + ").");
                return factory;
            } catch (RuntimeException e) {
                System.out.println("TLS keystore failed (" + e.getMessage() + "); using plain sockets.");
            }
        } else {
            System.out.println("TLS not configured (set -Dmirage.keystore=...); using plain sockets.");
        }
        return ServerSocketFactory.getDefault();
    }
}
