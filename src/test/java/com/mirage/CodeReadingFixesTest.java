package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.Alert;
import com.mirage.common.AttackEvent;
import com.mirage.common.Classification;
import com.mirage.common.ScopeGuard;
import com.mirage.common.Severity;
import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;
import com.mirage.console.AlertBacklog;
import com.mirage.console.AlertBus;
import com.mirage.console.ConsoleServer;
import com.mirage.console.SensorRegistry;
import com.mirage.console.ThreatLevel;
import com.mirage.console.ThreatTracker;
import com.mirage.sensor.SensorLink;
import com.mirage.specter.AttackStrategy;
import com.mirage.specter.AttackTrace;
import com.mirage.specter.Attacker;
import com.mirage.specter.Campaign;
import com.mirage.specter.Target;
import org.junit.jupiter.api.Test;

import javax.net.ServerSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** B-33: the smaller issues found by reading the code, one test each. */
class CodeReadingFixesTest {

    private static final String IP = "10.10.0.5";

    private static Alert alert(Severity severity, String technique) {
        return new Alert(IP, new Classification("test " + technique, technique, severity), Instant.now());
    }

    // ---- 1. AlertBus isolates listeners --------------------------------------------------

    @Test
    void aThrowingAlertListenerDoesNotStopTheOthersOrTheEngine() {
        AlertBus bus = new AlertBus();
        List<Alert> seen = new CopyOnWriteArrayList<>();
        bus.subscribe(a -> {
            throw new IllegalStateException("dashboard bug");
        });
        bus.subscribe(seen::add);
        assertDoesNotThrow(() -> bus.publish(alert(Severity.HIGH, "T1110")));
        assertEquals(1, seen.size());
    }

    // ---- 2. a persistent accept error does not spin ----------------------------------------

    @Test
    void aBrokenAcceptBacksOffInsteadOfSpinning() throws Exception {
        AtomicInteger accepts = new AtomicInteger();
        ServerSocketFactory broken = new ServerSocketFactory() {
            private ServerSocket failing() throws IOException {
                return new ServerSocket() {
                    @Override
                    public Socket accept() throws IOException {
                        accepts.incrementAndGet();
                        throw new IOException("Too many open files");
                    }
                };
            }

            @Override
            public ServerSocket createServerSocket(int port) throws IOException {
                return failing();
            }

            @Override
            public ServerSocket createServerSocket(int port, int backlog) throws IOException {
                return failing();
            }

            @Override
            public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
                return failing();
            }
        };
        ConsoleServer server = new ConsoleServer(broken, new LinkedBlockingQueue<>(), new SensorRegistry());
        server.start(0);
        try {
            Thread.sleep(1000);
        } finally {
            server.stop();
        }
        assertTrue(accepts.get() < 50, "accept() retried " + accepts.get() + " times in 1 s");
    }

    // ---- 3. the Console knows the Sensor before its first event -----------------------------

    @Test
    void aSensorLinkSendsItsHeartbeatBeforeAnyEvent() throws Exception {
        try (ServerSocket console = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            SensorLink link = new SensorLink("s-first", javax.net.SocketFactory.getDefault(), "127.0.0.1",
                    console.getLocalPort(), c -> { });
            link.sendEvent(new AttackEvent(IP, 4444, "ftp", Action.CONNECT, "", Instant.now())); // before connecting
            link.start();
            try (Socket s = console.accept()) {
                s.setSoTimeout(3000);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                assertTrue(in.readLine().contains("HEARTBEAT"), "the first line names the Sensor");
                assertTrue(in.readLine().contains("EVENT"));
            } finally {
                link.close();
            }
        }
    }

    // ---- 5. a late ACK for a superseded block does not neutralise -------------------------

    private static TraceEvent trace(String stage, String text) {
        return new TraceEvent(Instant.now(), "console", "t", stage, IP, TraceLevel.INFO, text);
    }

    @Test
    void aLateBlockAckAfterAnUnblockIsIgnored() {
        ThreatTracker t = new ThreatTracker();
        t.onAlert(alert(Severity.CRITICAL, "T1078"));
        t.onTrace(trace("dispatch", "broadcast BLOCK " + IP + " to 2 sensor(s)"));
        assertEquals(Optional.of(ThreatLevel.NEUTRALISED), t.onTrace(trace("ack", "s1 confirms: BLOCK " + IP + " applied")));
        t.onTrace(trace("dispatch", "broadcast UNBLOCK " + IP + " to 2 sensor(s)"));
        assertEquals(Optional.of(ThreatLevel.COMPROMISED), t.onTrace(trace("ack", "s1 confirms: UNBLOCK " + IP + " applied")));
        assertEquals(Optional.empty(), t.onTrace(trace("ack", "s2 confirms: BLOCK " + IP + " applied")),
                "s2's late BLOCK ACK is for a block that was already lifted");
        assertEquals(ThreatLevel.COMPROMISED, t.levelOf(IP));
    }

    @Test
    void aTarpitAfterABlockStillLetsTheBlockAckNeutralise() {
        ThreatTracker t = new ThreatTracker();
        t.onAlert(alert(Severity.CRITICAL, "T1078"));
        t.onTrace(trace("dispatch", "broadcast BLOCK " + IP + " to 1 sensor(s)"));
        t.onTrace(trace("dispatch", "broadcast TARPIT " + IP + " to 1 sensor(s)"));
        assertEquals(Optional.of(ThreatLevel.NEUTRALISED), t.onTrace(trace("ack", "s1 confirms: BLOCK " + IP + " applied")));
    }

    // ---- 7. alerts raised before the dashboard subscribes are not lost ----------------------

    @Test
    void alertsBeforeTheDashboardAttachesAreReplayedThenLive() {
        AlertBacklog backlog = new AlertBacklog(100);
        backlog.accept(alert(Severity.MEDIUM, "T1046"));
        backlog.accept(alert(Severity.HIGH, "T1110"));
        List<Alert> seen = new CopyOnWriteArrayList<>();
        backlog.attach(seen::add);
        backlog.accept(alert(Severity.CRITICAL, "T1078"));
        assertEquals(List.of("T1046", "T1110", "T1078"),
                seen.stream().map(a -> a.classification().attackTechnique()).toList());
    }

    // ---- 8. Specter's attack threads don't pile up ------------------------------------------

    @Test
    void attackThreadsAreDaemonsAndExitWhenIdle() throws Exception {
        AtomicReference<Thread> worker = new AtomicReference<>();
        CountDownLatch ran = new CountDownLatch(1);
        AttackStrategy record = new AttackStrategy() {
            @Override
            public void run(Target target, AttackTrace trace) {
                worker.set(Thread.currentThread());
                ran.countDown();
            }

            @Override
            public String name() {
                return "record";
            }
        };
        Attacker attacker = new Attacker(new ScopeGuard(List.of("127.0.0.1/32")), m -> { }, Duration.ofMillis(300));
        attacker.launch(new Campaign("c", List.of(record)), new Target("127.0.0.1", 8081));
        assertTrue(ran.await(3, TimeUnit.SECONDS));
        assertTrue(worker.get().isDaemon(), "an attack thread must not keep Specter's JVM alive");
        worker.get().join(3000);
        assertFalse(worker.get().isAlive(), "an idle attack thread exits");
    }
}
