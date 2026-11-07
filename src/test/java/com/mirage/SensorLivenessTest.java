package com.mirage;

import com.mirage.common.LabConfig;
import com.mirage.console.ConsoleServer;
import com.mirage.console.SensorRegistry;
import org.junit.jupiter.api.Test;

import javax.net.ServerSocketFactory;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-11: two Sensors on one machine get different ids, a Sensor that disconnects is reported
 * once (not as "possible tamper" every sweep), and a silent connected one is warned once.
 */
class SensorLivenessTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Instant T0 = Instant.parse("2026-11-08T10:00:00Z");

    // ---- ids --------------------------------------------------------------------------

    @Test
    void twoSensorsOnOneHostGetDifferentIds() {
        String a = LabConfig.from(Map.of()).sensorId("Knight_27");
        String b = LabConfig.from(Map.of("decoy.http.port", "9081")).sensorId("Knight_27");
        assertNotEquals(a, b);
        assertTrue(a.contains("Knight_27") && b.contains("9081"), a + " / " + b);
        assertEquals(a, LabConfig.from(Map.of()).sensorId("Knight_27"), "stable across restarts");
    }

    @Test
    void theIdCanBeSetExplicitly() {
        LabConfig config = LabConfig.from(LabConfig.parseArgs(new String[]{"--sensor-id", "lab-a"}));
        assertEquals("lab-a", config.sensorId("Knight_27"));
    }

    // ---- liveness ---------------------------------------------------------------------

    @Test
    void aSilentConnectedSensorIsWarnedOnceUntilItRecovers() {
        SensorRegistry r = new SensorRegistry();
        r.linkOpened("s1");
        r.markSeen("s1", T0);
        assertEquals(List.of("s1"), r.newlySilent(TIMEOUT, T0.plusSeconds(31)));
        assertEquals(List.of(), r.newlySilent(TIMEOUT, T0.plusSeconds(60)), "warned once, not every sweep");
        r.markSeen("s1", T0.plusSeconds(61));
        assertEquals(List.of(), r.newlySilent(TIMEOUT, T0.plusSeconds(70)));
        assertEquals(List.of("s1"), r.newlySilent(TIMEOUT, T0.plusSeconds(100)), "silent again: warned again");
    }

    @Test
    void aDisconnectedSensorIsNotCalledATamper() {
        SensorRegistry r = new SensorRegistry();
        r.linkOpened("s1");
        r.markSeen("s1", T0);
        assertTrue(r.linkClosed("s1"), "its last link closed");
        assertFalse(r.isConnected("s1"));
        assertEquals(List.of(), r.newlySilent(TIMEOUT, T0.plusSeconds(3600)));
    }

    @Test
    void aReconnectOverlappingTheOldLinkStaysConnected() {
        SensorRegistry r = new SensorRegistry();
        r.linkOpened("s1");
        r.linkOpened("s1"); // reconnected before the old link's close was noticed
        assertFalse(r.linkClosed("s1"));
        assertTrue(r.isConnected("s1"));
    }

    @Test
    void theConsoleMarksASensorDisconnectedWhenItsLinkCloses() throws Exception {
        SensorRegistry registry = new SensorRegistry();
        ConsoleServer server = new ConsoleServer(ServerSocketFactory.getDefault(), new LinkedBlockingQueue<>(), registry);
        int port = server.start(0);
        try {
            try (Socket s = new Socket("127.0.0.1", port)) {
                OutputStream out = s.getOutputStream();
                out.write(("{\"type\":\"HEARTBEAT\",\"sensorId\":\"s9\",\"timestamp\":\"" + Instant.now()
                        + "\"}\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                waitUntil(() -> registry.isKnown("s9") && registry.isConnected("s9"));
            }
            waitUntil(() -> !registry.isConnected("s9"));
        } finally {
            server.stop();
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier ok) throws InterruptedException {
        long end = System.currentTimeMillis() + 3000;
        while (!ok.getAsBoolean()) {
            assertTrue(System.currentTimeMillis() < end, "condition not reached in 3 s");
            Thread.sleep(20);
        }
    }
}
