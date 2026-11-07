package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.sensor.FakeHttpLoginDecoy;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 4: the decoy lets a trap credential "in" and serves many connections in parallel. */
class DecoyDeceptionTest {

    private static String basic(String creds) {
        return Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
    }

    private static String probe(int port, String creds) throws Exception {
        try (Socket s = new Socket("127.0.0.1", port)) {
            OutputStream out = s.getOutputStream();
            out.write(("GET / HTTP/1.0\r\nAuthorization: Basic " + basic(creds) + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            return in.readLine();
        }
    }

    @Test
    void trapCredentialIsGrantedAndReportedAsDecoyAccess() throws Exception {
        BlockingQueue<AttackEvent> events = new LinkedBlockingQueue<>();
        FakeHttpLoginDecoy decoy = new FakeHttpLoginDecoy(0, Set.of("svc_backup:S3cr3tBackup!"));
        decoy.start(events::add);
        try {
            assertTrue(probe(decoy.boundPort(), "svc_backup:S3cr3tBackup!").contains("200"),
                    "the trap credential should be accepted");

            boolean sawAccess = false;
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline) {
                AttackEvent e = events.poll(500, TimeUnit.MILLISECONDS);
                if (e != null && e.action() == Action.DECOY_ACCESS) {
                    sawAccess = true;
                    break;
                }
            }
            assertTrue(sawAccess, "a DECOY_ACCESS event should be reported");
        } finally {
            decoy.stop();
        }
    }

    @Test
    void ordinaryCredentialStillGets401() throws Exception {
        FakeHttpLoginDecoy decoy = new FakeHttpLoginDecoy(0, Set.of("svc_backup:S3cr3tBackup!"));
        decoy.start(e -> { });
        try {
            assertTrue(probe(decoy.boundPort(), "admin:password").contains("401"));
        } finally {
            decoy.stop();
        }
    }

    @Test
    void manyConnectionsAreServedInParallel() throws Exception {
        FakeHttpLoginDecoy decoy = new FakeHttpLoginDecoy(0);
        decoy.start(e -> { });
        int clients = 6;
        CountDownLatch done = new CountDownLatch(clients);
        List<String> firstLines = new CopyOnWriteArrayList<>();
        try {
            for (int i = 0; i < clients; i++) {
                new Thread(() -> {
                    try {
                        firstLines.add(probe(decoy.boundPort(), "admin:nope"));
                    } catch (Exception ignored) {
                        // counted as a failure by the latch not reaching zero
                    } finally {
                        done.countDown();
                    }
                }).start();
            }
            assertTrue(done.await(5, TimeUnit.SECONDS), "all clients should be served promptly in parallel");
            assertEquals(clients, firstLines.size());
            assertTrue(firstLines.stream().allMatch(l -> l != null && l.contains("401")));
        } finally {
            decoy.stop();
        }
    }
}
