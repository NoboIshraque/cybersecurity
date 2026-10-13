package com.mirage;

import com.mirage.common.Alert;
import com.mirage.common.AttackEvent;
import com.mirage.common.CommandType;
import com.mirage.common.Honeytoken;
import com.mirage.common.ResponseCommand;
import com.mirage.common.ScopeGuard;
import com.mirage.common.Severity;
import com.mirage.console.AlertBus;
import com.mirage.console.AlertLedger;
import com.mirage.console.CommandSink;
import com.mirage.console.ConsoleServer;
import com.mirage.console.CorrelationEngine;
import com.mirage.console.HoneytokenStore;
import com.mirage.console.Persistence;
import com.mirage.console.ResponsePolicy;
import com.mirage.console.SensorRegistry;
import com.mirage.console.detection.DetectionRule;
import com.mirage.console.detection.HoneytokenUseRule;
import com.mirage.sensor.FakeHttpLoginDecoy;
import com.mirage.sensor.SensorClient;
import com.mirage.specter.Attacker;
import com.mirage.specter.Campaign;
import com.mirage.specter.HoneytokenReplayAttack;
import com.mirage.specter.Target;
import org.junit.jupiter.api.Test;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Capstone: an attacker replays a planted honeytoken against a decoy, and the
 * whole chain fires — decoy capture -> Sensor -> Console -> classification ->
 * ledger -> a scoped BLOCK dispatched back toward the Sensors.
 */
class EndToEndTest {

    @Test
    void honeytokenReplayRaisesCriticalAndDispatchesBlock() throws Exception {
        String honeytoken = "svc_backup:S3cr3tBackup!";

        // --- Console side ---
        BlockingQueue<Alert> alerts = new LinkedBlockingQueue<>();
        BlockingQueue<ResponseCommand> commands = new LinkedBlockingQueue<>();
        AlertBus bus = new AlertBus();
        bus.subscribe(alerts::add);
        CommandSink sink = commands::add;

        ScopeGuard guard = new ScopeGuard(List.of("127.0.0.1/32"));
        HoneytokenStore store = new HoneytokenStore();
        store.plant(new Honeytoken("ht-1", honeytoken, "config/backup.cfg"));
        ResponsePolicy policy = new ResponsePolicy(guard, sink);
        AlertLedger ledger = new AlertLedger();
        List<DetectionRule> rules = List.of(new HoneytokenUseRule(store));

        BlockingQueue<AttackEvent> queue = new LinkedBlockingQueue<>();
        CorrelationEngine engine =
                new CorrelationEngine(queue, rules, ledger, bus, policy, Persistence.NONE);
        Thread engineThread = new Thread(engine, "engine");
        engineThread.setDaemon(true);
        engineThread.start();

        SensorRegistry registry = new SensorRegistry();
        ConsoleServer server = new ConsoleServer(ServerSocketFactory.getDefault(), queue, registry);
        int consolePort = server.start(0);

        // --- Sensor side: a decoy reporting over the real socket link ---
        SensorClient client =
                new SensorClient("sensor-a", SocketFactory.getDefault(), "127.0.0.1", consolePort);
        FakeHttpLoginDecoy decoy = new FakeHttpLoginDecoy(0);
        decoy.start(event -> {
            try {
                client.sendEvent(event);
            } catch (IOException ignored) {
                // link down
            }
        });
        int decoyPort = decoy.boundPort();

        // --- Red team: replay the stolen honeytoken against the decoy ---
        Attacker attacker = new Attacker(guard);
        attacker.launch(new Campaign("honeytoken",
                        List.of(new HoneytokenReplayAttack(honeytoken))),
                new Target("127.0.0.1", decoyPort));

        // --- Assert the whole chain fired ---
        Alert alert = null;
        long deadline = System.currentTimeMillis() + 5000;
        while (alert == null && System.currentTimeMillis() < deadline) {
            alert = alerts.poll(500, TimeUnit.MILLISECONDS);
        }
        assertNotNull(alert, "a honeytoken alert should be raised end-to-end");
        assertEquals(Severity.CRITICAL, alert.classification().severity());
        assertEquals("127.0.0.1", alert.sourceIp());
        assertTrue(ledger.verify(), "the ledger should be intact");

        ResponseCommand cmd = commands.poll(2, TimeUnit.SECONDS);
        assertNotNull(cmd, "a scoped response command should be dispatched");
        assertEquals(CommandType.BLOCK, cmd.type());
        assertEquals("127.0.0.1", cmd.targetIp());

        attacker.shutdown();
        decoy.stop();
        client.close();
        server.stop();
    }
}
