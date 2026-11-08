package com.mirage;

import com.mirage.common.AttackEvent;
import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceSink;
import com.mirage.sensor.FakeFtpDecoy;
import com.mirage.specter.CounterWatch;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FC-01: two tests failed in about 3 of 5 full-suite runs on a busy machine. Both were real
 * ordering bugs, made deterministic here by slowing the step that loses the race.
 */
class AcceptRaceTest {

    /**
     * The decoy closed an oversized-line connection before it reported it (try-with-resources
     * closes before the catch runs), so whoever saw the hang-up could look for the report too
     * early. A slow event sink makes that window wide: the report must still come first.
     */
    @Test
    void aDecoyReportsAnOversizedLineBeforeItHangsUp() throws Exception {
        List<AttackEvent> events = new CopyOnWriteArrayList<>();
        FakeFtpDecoy decoy = new FakeFtpDecoy(0);
        decoy.start(e -> {
            if (e.payload() != null && e.payload().contains("line longer than")) {
                sleep(300); // a busy Console link
            }
            events.add(e);
        });
        try (Socket s = new Socket("127.0.0.1", decoy.boundPort())) {
            Thread writer = new Thread(() -> {
                try {
                    OutputStream out = s.getOutputStream();
                    byte[] chunk = new byte[8192];
                    java.util.Arrays.fill(chunk, (byte) 'A');
                    for (int i = 0; i < 64; i++) {
                        out.write(chunk);
                    }
                } catch (IOException ignored) {
                    // cut off: expected
                }
            });
            writer.start();
            s.setSoTimeout(5000);
            InputStream in = s.getInputStream();
            try {
                while (in.read() != -1) {
                    // drain the banner until the decoy hangs up
                }
            } catch (SocketException reset) {
                // hung up with a reset: also fine
            }
            assertTrue(events.stream().anyMatch(e -> e.payload() != null && e.payload().contains("line longer than")),
                    "the oversized line is reported before the connection is closed: " + events);
            writer.join(5000);
        } finally {
            decoy.stop();
        }
    }

    /**
     * The counter-watch listened with the default backlog of 50. While its accept thread was
     * busy (a slow shell, a loaded machine), Windows refused connection 51 onwards. Stall the
     * accept thread on purpose and connect 120 times: every connection must still get in and
     * be counted.
     */
    @Test
    void theCounterWatchQueuesABurstWhileItsAcceptThreadIsBusy() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        List<TraceEvent> lines = new CopyOnWriteArrayList<>();
        TraceSink slow = event -> {
            if (event.text().startsWith("INCOMING connection #1 ")) {
                await(release); // the accept thread is stuck here
            }
            lines.add(event);
        };
        CounterWatch watch = new CounterWatch(0, slow);
        int port = watch.start();
        List<Socket> sockets = new ArrayList<>();
        int refused = 0;
        try {
            for (int i = 0; i < 120; i++) {
                try {
                    sockets.add(new Socket("127.0.0.1", port));
                } catch (IOException e) {
                    refused++;
                }
            }
            release.countDown();
            assertEquals(0, refused, "connections refused while the accept thread was busy");
            long end = System.currentTimeMillis() + 5_000;
            while (watch.hits() < 120 && System.currentTimeMillis() < end) {
                Thread.sleep(20);
            }
            assertEquals(120, watch.hits(), "every connection is counted");
        } finally {
            release.countDown();
            for (Socket s : sockets) {
                s.close();
            }
            watch.stop();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
