package com.mirage.console;

import com.mirage.common.AttackEvent;
import com.mirage.common.Honeytoken;
import com.mirage.common.LabConfig;
import com.mirage.common.ScopeGuard;
import com.mirage.common.trace.TraceBus;
import com.mirage.common.LinkSecurity;
import com.mirage.console.db.AlertDao;
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
        // A-02: plain text on 127.0.0.1 only, or mutual TLS; a broken TLS set-up refuses to start.
        LinkSecurity link;
        try {
            link = LinkSecurity.fromEnvironment();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Refusing to start: " + e.getMessage(), e);
        }
        System.out.println("Sensor link: " + link.describe() + ".");
        // Only advertise an address inside the lab range (skips VirtualBox / VPN adapters): an
        // address outside it could never be attacked or defended, so it would only mislead.
        java.util.Optional<String> lanIp = LabConfig.primaryLanIp(guard::isAuthorised)
                .filter(guard::isAuthorised);
        lanIp.ifPresentOrElse(
                ip -> System.out.println("This host's LAN address: " + ip
                        + "  (on the attacker laptop set console.host=" + ip + ")"),
                () -> System.out.println("No LAN address inside the lab range " + config.labCidrs()
                        + "; single-machine demo on 127.0.0.1 (set lab.cidrs to your network for two laptops)."));
        // A-06: the ledger key survives restarts (data/ledger-key.pem), locked when a password is set.
        String ledgerPassword = System.getenv(KeyPairSigner.PASSWORD_ENV);
        KeyPairSigner signer;
        try {
            signer = KeyPairSigner.loadOrCreate(Path.of("data"), ledgerPassword);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Refusing to start: " + e.getMessage(), e);
        }
        boolean locked = ledgerPassword != null && !ledgerPassword.isEmpty();
        System.out.println("Ledger key " + signer.keyId() + " (public key: data/" + KeyPairSigner.PUBLIC_KEY_FILE + ")"
                + (locked ? ", locked with " + KeyPairSigner.PASSWORD_ENV + "." : "."));
        if (!locked) {
            System.out.println("[warn] data/" + KeyPairSigner.KEY_FILE + " is not password-protected: set "
                    + KeyPairSigner.PASSWORD_ENV + " to lock it.");
        }

        // A-07: the database file is encrypted when MIRAGE_DB_PASSWORD is set.
        String dbPassword = System.getenv(Database.PASSWORD_ENV);
        Database database;
        try {
            database = Database.openFile(Path.of("data"), dbPassword);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Refusing to start: " + e.getMessage(), e);
        }
        Persistence persistence = new H2Persistence(database, signer.keyId());
        boolean dbLocked = dbPassword != null && !dbPassword.isEmpty();
        System.out.println("Persistence ready (H2 at ./data/mirage" + (dbLocked ? ", encrypted)." : ")."));
        if (!dbLocked) {
            System.out.println("[warn] ./data/mirage is not encrypted: set " + Database.PASSWORD_ENV
                    + " to encrypt a new database (the ledger chain check still catches edits).");
        }

        // B-30: continue the stored chain instead of starting a new one, and check it now.
        AlertDao alertDao = new AlertDao(database);
        List<com.mirage.common.LedgerEntry> stored = alertDao.loadChain();
        AlertLedger ledger = new AlertLedger(signer, stored);
        int broken = ledger.firstBrokenIndex();
        int legacy = alertDao.legacyCount();
        System.out.println("Ledger: " + stored.size() + " stored alert(s) loaded, chain "
                + (broken < 0 ? "intact." : "BROKEN at entry #" + broken + " (changed since it was written)."));
        if (broken >= 0) {
            System.out.println("[warn] the stored ledger fails verification at entry #" + broken
                    + "; the dashboard marks it in red.");
        }
        if (legacy > 0) {
            System.out.println("Ledger: " + legacy + " older alert row(s) from before the key was saved are kept "
                    + "in the database but can't be verified, so they are not part of the chain.");
        }
        AlertBus bus = new AlertBus();
        bus.subscribe(alert -> System.out.println("[alert] " + alert.canonical()));

        HoneytokenStore honeytokens = new HoneytokenStore();
        Honeytoken backupToken = new Honeytoken("ht-1", "svc_backup:S3cr3tBackup!", "config/backup.cfg");
        honeytokens.plant(backupToken);
        persistence.honeytokenPlanted(backupToken);

        // The live process shells subscribe to this bus; nothing is printed until one attaches.
        TraceBus trace = new TraceBus();

        BlockingQueue<AttackEvent> events = new LinkedBlockingQueue<>();
        SensorRegistry registry = new SensorRegistry();
        ConsoleServer server = new ConsoleServer(link.serverFactory(), events, registry)
                .setBindAddress(link.bindAddress());
        server.setTrace(trace);
        server.setPersistence(persistence); // sensor table (B-30)
        AttackerDirectory directory = new AttackerDirectory();

        // The server is the CommandSink: active-defence responses broadcast to the Sensors,
        // and each one is recorded in the block_rule table (B-30).
        CommandSink commands = CommandSink.recorded(server, persistence);
        ResponsePolicy policy = new ResponsePolicy(guard, commands);
        ResponseController responses = new ResponseController(guard, commands);
        responses.setTrace(trace);

        // Operator-triggered counter-offensive: mirror the attacker's own technique back,
        // through the same ScopeGuard, so it can only ever reach a source inside the lab range.
        Attacker counterAttacker = new Attacker(guard, line -> System.out.println("[counter] " + line));
        counterAttacker.setTrace(trace, "op");
        CounterOffensive counter = new CounterOffensive(
                counterAttacker, new MirrorCounterStrategy(config.counterWatchPort()), config.counterWatchPort());
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
}
