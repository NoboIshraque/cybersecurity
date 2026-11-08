package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.Alert;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerProfile;
import com.mirage.common.Classification;
import com.mirage.common.CommandType;
import com.mirage.common.Honeytoken;
import com.mirage.common.ResponseCommand;
import com.mirage.common.ScopeGuard;
import com.mirage.common.Severity;
import com.mirage.console.AlertBus;
import com.mirage.console.AlertLedger;
import com.mirage.console.AutoResponse;
import com.mirage.console.CommandSink;
import com.mirage.console.ConsoleServer;
import com.mirage.console.CorrelationEngine;
import com.mirage.console.H2Persistence;
import com.mirage.console.Persistence;
import com.mirage.console.ResponsePolicy;
import com.mirage.console.SensorRegistry;
import com.mirage.console.db.Database;
import com.mirage.console.detection.DecoyAccessRule;
import com.mirage.common.LedgerEntry;
import com.mirage.sensor.SensorClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-30: the schema promised sensor, decoy, attacker_profile, honeytoken and block_rule tables,
 * but nothing ever wrote them, and stored events lacked the decoy type and the Sensor. Agreed:
 * fill the four the Console has data for, drop {@code decoy}.
 */
class PersistenceTablesTest {

    private static final String TOKEN = "svc_backup:S3cr3tBackup!";

    private String url;
    private Database db;
    private H2Persistence persistence;
    private Connection check;

    @BeforeEach
    void open() throws Exception {
        // FC-02: no query cache. awaitRows polls with the same SQL text, and H2 may keep a cached
        // result computed while the server's write was landing: the poll then sees the old answer
        // until another write, though the row is there (a different query, or connection, sees it).
        url = "jdbc:h2:mem:tables-" + UUID.randomUUID() + ";QUERY_CACHE_SIZE=0";
        db = new Database(url);
        persistence = new H2Persistence(db, "test-key");
        check = DriverManager.getConnection(url); // a second connection to read what was written
    }

    @AfterEach
    void close() throws Exception {
        check.close();
        db.close();
    }

    private List<List<Object>> rows(String sql) throws Exception {
        List<List<Object>> out = new ArrayList<>();
        try (Statement st = check.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= cols; i++) {
                    row.add(rs.getObject(i));
                }
                out.add(row);
            }
        }
        return out;
    }

    @Test
    void theUnusedDecoyTableIsGoneFromANewDatabase() throws Exception {
        assertTrue(rows("SELECT table_name FROM information_schema.tables WHERE UPPER(table_name) = 'DECOY'").isEmpty());
    }

    @Test
    void sensorsAreRecordedAndEventsKeepTheirDecoyTypeAndSensor() throws Exception {
        persistence.sensorSeen("sensor-a", "127.0.0.1", Instant.now());
        persistence.sensorSeen("sensor-a", "127.0.0.1", Instant.now()); // a later heartbeat: same row
        persistence.saveEvent(new AttackEvent("10.10.0.5", 1, "ftp", Action.CONNECT, "", Instant.now())
                .withSensor("sensor-a"));

        assertEquals(List.of(List.of("sensor-a", "127.0.0.1", "ONLINE")),
                rows("SELECT hostname, ip, status FROM sensor"));
        assertEquals(List.of(List.of("ftp", "sensor-a")),
                rows("SELECT e.decoy_type, s.hostname FROM attack_event e JOIN sensor s ON e.sensor_id = s.id"));

        persistence.sensorOffline("sensor-a");
        assertEquals(List.of(List.of("OFFLINE")), rows("SELECT status FROM sensor"));
    }

    @Test
    void honeytokensArePlantedOnceAndMarkedWhenUsed() throws Exception {
        Honeytoken token = new Honeytoken("ht-1", TOKEN, "config/backup.cfg");
        persistence.honeytokenPlanted(token);
        persistence.honeytokenPlanted(token); // next start: not planted twice
        assertEquals(List.of(List.of(TOKEN, "config/backup.cfg", false)),
                rows("SELECT token_value, planted_location, used FROM honeytoken"));

        persistence.saveEvent(new AttackEvent("10.10.0.5", 1, "http-login", Action.LOGIN_ATTEMPT, TOKEN, Instant.now()));
        assertEquals(List.of(List.of(true)), rows("SELECT used FROM honeytoken WHERE used_ts IS NOT NULL"));
    }

    @Test
    void everyCommandSentIsRecordedAndAnUnblockClosesTheBlock() throws Exception {
        List<ResponseCommand> sent = new CopyOnWriteArrayList<>();
        CommandSink sink = CommandSink.recorded(sent::add, persistence);

        sink.broadcast(new ResponseCommand(CommandType.TARPIT, "10.10.0.5"));
        sink.broadcast(new ResponseCommand(CommandType.BLOCK, "10.10.0.5"));
        assertEquals(2, sent.size(), "the commands still go out");
        assertEquals(List.of(List.of("TARPIT", true), List.of("BLOCK", true)),
                rows("SELECT scope, active FROM block_rule WHERE src_ip = '10.10.0.5' ORDER BY id"));

        sink.broadcast(new ResponseCommand(CommandType.UNBLOCK, "10.10.0.5"));
        assertTrue(rows("SELECT id FROM block_rule WHERE active").isEmpty(), "the unblock closes them");
    }

    @Test
    void attackerProfilesSummariseEveryTechniqueAndTheWorstSeverity() throws Exception {
        AttackerProfile profile = new AttackerProfile("10.10.0.5");
        for (int i = 0; i < 4; i++) {
            profile.add(new AttackEvent("10.10.0.5", 1, "ftp", Action.CONNECT, "", Instant.now()));
        }
        persistence.profileAlerted(profile, new Alert("10.10.0.5",
                new Classification("scan", "T1046", Severity.MEDIUM), Instant.now()));
        persistence.profileAlerted(profile, new Alert("10.10.0.5",
                new Classification("brute", "T1110", Severity.HIGH), Instant.now()));
        persistence.profileAlerted(profile, new Alert("10.10.0.5",
                new Classification("scan again", "T1046", Severity.LOW), Instant.now()));

        assertEquals(List.of(List.of("T1046,T1110", "HIGH", 4)),
                rows("SELECT techniques, severity, event_count FROM attacker_profile WHERE src_ip = '10.10.0.5'"));
    }

    // ---- wiring: the running Console actually calls these ---------------------------------

    @Test
    void theConsoleServerRecordsSensorsAsTheyConnectAndLeave() throws Exception {
        ConsoleServer server = new ConsoleServer(ServerSocketFactory.getDefault(), new LinkedBlockingQueue<>(),
                new SensorRegistry()).setPersistence(persistence);
        int port = server.start(0);
        try {
            try (SensorClient client = new SensorClient("sensor-w", SocketFactory.getDefault(), "127.0.0.1", port)) {
                client.sendHeartbeat();
                awaitRows("SELECT id FROM sensor WHERE hostname = 'sensor-w' AND status = 'ONLINE'");
            }
            awaitRows("SELECT id FROM sensor WHERE hostname = 'sensor-w' AND status = 'OFFLINE'");
        } finally {
            server.stop();
        }
    }

    @Test
    void theEngineUpdatesTheProfileWhenItRaisesAnAlert() throws Exception {
        List<String> profiled = new CopyOnWriteArrayList<>();
        Persistence spy = new Persistence() {
            @Override public void saveEvent(AttackEvent event) { }
            @Override public void saveAlert(LedgerEntry entry) { }
            @Override public void profileAlerted(AttackerProfile profile, Alert alert) {
                profiled.add(profile.sourceIp() + " " + alert.classification().attackTechnique());
            }
        };
        LinkedBlockingQueue<AttackEvent> queue = new LinkedBlockingQueue<>();
        CorrelationEngine engine = new CorrelationEngine(queue, List.of(new DecoyAccessRule()), new AlertLedger(),
                new AlertBus(), new ResponsePolicy(new ScopeGuard(List.of("10.10.0.0/24")), CommandSink.NONE), spy);
        engine.setAutoResponse(new AutoResponse(false));
        Thread t = new Thread(engine, "engine-tables-test");
        t.setDaemon(true);
        t.start();
        try {
            queue.add(new AttackEvent("10.10.0.9", 1, "http-login", Action.DECOY_ACCESS, TOKEN, Instant.now()));
            long deadline = System.currentTimeMillis() + 3000;
            while (profiled.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(List.of("10.10.0.9 T1078"), profiled);
        } finally {
            t.interrupt();
        }
    }

    /** Polls until a row appears (it returns as soon as it does; the ceiling only bounds a failure). */
    private void awaitRows(String sql) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (rows(sql).isEmpty()) {
            if (System.currentTimeMillis() > deadline) {
                fail("no row for: " + sql + "; sensor table: " + rows("SELECT * FROM sensor"));
            }
            Thread.sleep(25);
        }
    }
}
