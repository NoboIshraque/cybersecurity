package com.mirage;

import com.mirage.common.trace.TraceEvent;
import com.mirage.specter.CounterWatch;
import org.junit.jupiter.api.Test;

import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CounterWatchTest {

    @Test
    void logsAnIncomingConnectionAndSendsNothingBack() throws Exception {
        List<TraceEvent> seen = new CopyOnWriteArrayList<>();
        CounterWatch watch = new CounterWatch(0, seen::add);
        int port = watch.start();
        try {
            try (Socket s = new Socket("127.0.0.1", port)) {
                s.getOutputStream().write("hello\r\n".getBytes());
                s.getOutputStream().flush();
                // The watcher must never write anything back.
                s.setSoTimeout(600);
                int b = -1;
                try {
                    b = s.getInputStream().read();
                } catch (java.net.SocketTimeoutException expected) {
                    b = -2; // timed out waiting — good, nothing was sent
                }
                assertTrue(b == -1 || b == -2, "counter-watch must not send anything back");
            }

            long deadline = System.currentTimeMillis() + 3000;
            while (watch.hits() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(1, watch.hits());
            assertTrue(seen.stream().anyMatch(e -> e.text().contains("INCOMING")), seen.toString());
        } finally {
            watch.stop();
        }
    }
}
