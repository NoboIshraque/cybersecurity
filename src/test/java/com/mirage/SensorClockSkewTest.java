package com.mirage;

import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-14: liveness compared the Sensor's own clock with the Console's. On two laptops a Sensor
 * clock 45 s slow was reported "silent (possible tamper)" while it heartbeated, and one an hour
 * fast could die without a warning for an hour. Liveness now uses the Console's receive time;
 * the Sensor's timestamp only feeds a one-off clock-skew warning.
 */
class SensorClockSkewTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30); // ConsoleMain's dead-sensor timeout

    private final List<TraceEvent> traces = new CopyOnWriteArrayList<>();

    /** Sends one heartbeat stamped {@code skew} away from now, and waits until it is registered. */
    private SensorRegistry heartbeat(String id, Duration skew) throws Exception {
        SensorRegistry registry = new SensorRegistry();
        ConsoleServer server = new ConsoleServer(ServerSocketFactory.getDefault(), new LinkedBlockingQueue<>(), registry);
        server.setTrace(traces::add);
        int port = server.start(0);
        try (Socket s = new Socket("127.0.0.1", port)) {
            OutputStream out = s.getOutputStream();
            out.write(("{\"type\":\"HEARTBEAT\",\"sensorId\":\"" + id + "\",\"timestamp\":\""
                    + Instant.now().plus(skew) + "\"}\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            long deadline = System.currentTimeMillis() + 3000;
            while (!registry.isKnown(id) && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(registry.isKnown(id));
            Thread.sleep(100); // let the trace line land
        } finally {
            server.stop();
        }
        return registry;
    }

    @Test
    void aSensorWithASlowClockIsNotCalledSilentWhileItHeartbeats() throws Exception {
        SensorRegistry registry = heartbeat("slow-sensor", Duration.ofSeconds(-45));
        assertFalse(registry.deadSensors(TIMEOUT, Instant.now()).contains("slow-sensor"),
                "it heartbeated just now; its clock being 45 s slow must not make it look dead");
    }

    @Test
    void aSensorWithAFastClockIsStillReportedWhenItGoesSilent() throws Exception {
        SensorRegistry registry = heartbeat("fast-sensor", Duration.ofHours(1));
        assertTrue(registry.deadSensors(TIMEOUT, Instant.now().plusSeconds(31)).contains("fast-sensor"),
                "31 s after its last heartbeat it is overdue, whatever its clock said");
    }

    @Test
    void aSkewedClockIsPointedOutOnceInTheShell() throws Exception {
        heartbeat("slow-sensor", Duration.ofSeconds(-45));
        List<TraceEvent> warnings = traces.stream()
                .filter(t -> t.level() == TraceLevel.WARN && t.text().contains("clock")).toList();
        assertEquals(1, warnings.size(), traces.toString());
        assertTrue(warnings.get(0).text().contains("slow-sensor"), warnings.get(0).text());
    }
}
