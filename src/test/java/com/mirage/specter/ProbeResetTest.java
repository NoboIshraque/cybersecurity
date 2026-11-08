package com.mirage.specter;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-24: a blocked decoy resets the connection right after accepting it. If that reset reached
 * Specter before its request was written, the write failed and the attempt read "connection
 * failed" instead of "connection reset (blocked)": a race that failed StrategyOutcomeTest in
 * about 2 of 7 runs. This test forces the losing order every time: the reset has arrived
 * before the request is sent.
 */
class ProbeResetTest {

    private static final byte[] REQUEST = "GET / HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    @Test
    void aResetThatArrivesBeforeOurRequestIsStillABlock() throws Exception {
        try (ServerSocket blocked = new ServerSocket(0);
             Socket client = new Socket("127.0.0.1", blocked.getLocalPort())) {
            try (Socket accepted = blocked.accept()) {
                accepted.setSoLinger(true, 0); // close with a reset, like a blocked decoy
            }
            Thread.sleep(300); // let the reset land before we write

            ProbeReply reply = ProbeReply.exchange(client, REQUEST, 1000);

            assertEquals(ProbeReply.Outcome.DROPPED, reply.outcome);
            assertEquals("connection reset (blocked)", BruteForceAttack.describe(reply));
            assertEquals("CONNECTION BLOCKED", BruteForceAttack.bannerFor(reply.outcome));
        }
    }

    @Test
    void resetsAndAbortsOnConnectOrWriteAreBlocks() {
        for (String message : new String[]{
                "Connection reset",                                                     // Linux / JDK
                "Connection reset by peer",                                             // Windows
                "An established connection was aborted by the software in your host machine",
                "Software caused connection abort: socket write error",
                "Broken pipe"}) {
            ProbeReply reply = ProbeReply.failure(new SocketException(message));
            assertEquals(ProbeReply.Outcome.DROPPED, reply.outcome, message);
            assertEquals("connection reset (blocked)", BruteForceAttack.describe(reply), message);
        }
    }

    @Test
    void anythingElseIsStillReportedAsAFailedConnection() {
        for (IOException e : new IOException[]{
                new ConnectException("Connection refused: connect"),
                new SocketException("Network is unreachable"),
                new IOException("something else")}) {
            ProbeReply reply = ProbeReply.failure(e);
            assertTrue(BruteForceAttack.describe(reply).startsWith("connection failed ("), e.getMessage());
            assertTrue(reply.connectFailed());
        }
    }
}
