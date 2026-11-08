package com.mirage.specter;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FB-01: Specter called every failed connection a block. A closed port, a Sensor that is
 * down and an unreachable host all ended in "CONNECTION BLOCKED", so on two laptops a
 * firewall problem looked like the defender blocking the attacker. The probe also had no
 * connect timeout, so an unreachable host hung for about 21 s (Windows) before saying so.
 *
 * <p>A block is a reset from a decoy that accepted us; anything that never connected is
 * "CONNECTION FAILED", with the reason (refused, or timed out).
 */
class ProbeUnreachableTest {

    private static final byte[] REQUEST = "GET / HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    /** A port with nothing listening on it: bind an ephemeral port, then free it. */
    private static int closedPort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @Test
    void aClosedPortIsAFailedConnectionNotABlock() throws Exception {
        ProbeReply reply = ProbeReply.probe(new Target("127.0.0.1", closedPort()), REQUEST, 1000);

        assertEquals(ProbeReply.Outcome.UNREACHABLE, reply.outcome);
        assertTrue(reply.connectFailed());
        assertEquals("CONNECTION FAILED", BruteForceAttack.bannerFor(reply.outcome));
        assertTrue(BruteForceAttack.describe(reply).contains("refused"), BruteForceAttack.describe(reply));
    }

    @Test
    void everyCredentialStrategySaysFailedNotBlockedWhenNothingIsListening() throws Exception {
        Target target = new Target("127.0.0.1", closedPort());
        List<String> lines = new ArrayList<>();
        AttackTrace trace = (level, text) -> lines.add(level + " " + text);
        for (AttackStrategy strategy : List.of(
                new BruteForceAttack("admin", List.of("a", "b", "c")),
                new PasswordSprayAttack(List.of("alice", "bob"), "Winter2026!"),
                new HoneytokenReplayAttack("svc_backup:S3cr3tBackup!"))) {
            lines.clear();
            strategy.run(target, trace);
            assertTrue(lines.contains("BANNER CONNECTION FAILED"), strategy.name() + ": " + lines);
            assertTrue(lines.stream().noneMatch(l -> l.toLowerCase().contains("blocked")),
                    strategy.name() + ": " + lines);
            assertEquals(1, lines.stream().filter(l -> l.contains("connection failed")).count(),
                    strategy.name() + " should give up after the first failed connection: " + lines);
        }
    }

    @Test
    void aConnectTimeoutSaysWhyAndIsNotABlock() {
        ProbeReply reply = ProbeReply.failure(new SocketTimeoutException("Connect timed out"));

        assertEquals(ProbeReply.Outcome.UNREACHABLE, reply.outcome);
        String text = BruteForceAttack.describe(reply);
        assertTrue(text.startsWith("connection failed") && text.contains("timed out"), text);
    }

    @Test
    void aRefusedConnectionSaysNothingIsListening() {
        String text = BruteForceAttack.describe(ProbeReply.failure(new ConnectException("Connection refused: connect")));
        assertTrue(text.startsWith("connection failed") && text.contains("nothing listening"), text);
    }

    /**
     * 10.10.0.5 is in the default lab range but no host has it, so the SYN goes unanswered (or the network says
     * at once that there is no route). Either way the probe must give up within its connect
     * timeout instead of the OS default (about 21 s on Windows, minutes on Linux).
     */
    @Test
    void aSilentHostGivesUpWithinTheConnectTimeout() {
        ProbeReply reply = assertTimeoutPreemptively(Duration.ofSeconds(ProbeReply.CONNECT_TIMEOUT_MS / 1000 + 3),
                () -> ProbeReply.probe(new Target("10.10.0.5", 8081), REQUEST, 1000));

        assertEquals(ProbeReply.Outcome.UNREACHABLE, reply.outcome);
        assertEquals("CONNECTION FAILED", BruteForceAttack.bannerFor(reply.outcome));
    }
}
