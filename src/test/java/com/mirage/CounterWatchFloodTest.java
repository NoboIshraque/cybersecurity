package com.mirage;

import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceSink;
import com.mirage.specter.CounterWatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A-08: the attacker's counter-watch is reachable by anyone on the LAN, so a burst of
 * connections must not cost one thread each or flood its shell. It still only accepts and logs.
 */
class CounterWatchFloodTest {

    private final List<TraceEvent> lines = new CopyOnWriteArrayList<>();
    private final List<Socket> held = new ArrayList<>();
    private CounterWatch watch;

    @AfterEach
    void stop() {
        for (Socket s : held) {
            try {
                s.close();
            } catch (IOException ignored) {
                // closing
            }
        }
        if (watch != null) {
            watch.stop();
        }
    }

    private int start() throws IOException {
        TraceSink sink = lines::add;
        watch = new CounterWatch(0, sink);
        return watch.start();
    }

    private static long incomingThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith("incoming-") && t.isAlive()).count();
    }

    @Test
    void manyHeldConnectionsDoNotEachCostAThread() throws Exception {
        int port = start();
        for (int i = 0; i < 60; i++) {
            held.add(new Socket("127.0.0.1", port)); // connect and say nothing
        }
        Thread.sleep(200);
        assertTrue(incomingThreads() <= CounterWatch.WORKERS,
                "threads busy with incoming connections: " + incomingThreads());
    }

    @Test
    void aBurstIsCountedButOnlyPartlyLogged() throws Exception {
        int port = start();
        for (int i = 0; i < 100; i++) {
            try (Socket s = new Socket("127.0.0.1", port)) {
                s.getOutputStream().write("x\n".getBytes());
            }
        }
        long end = System.currentTimeMillis() + 5_000;
        while (watch.hits() < 100 && System.currentTimeMillis() < end) {
            Thread.sleep(20);
        }
        assertEquals(100, watch.hits(), "every connection is counted");
        long shown = lines.stream().filter(l -> l.text().startsWith("INCOMING connection")).count();
        assertTrue(shown <= CounterWatch.LOG_PER_WINDOW, "lines shown: " + shown);
        assertTrue(lines.stream().anyMatch(l -> l.text().contains("not shown")), "a summary says some were not shown");
    }
}
