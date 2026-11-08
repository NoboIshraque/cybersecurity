package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.CommandType;
import com.mirage.common.ResponseCommand;
import com.mirage.sensor.ConnectionGate;
import com.mirage.sensor.Decoy;
import com.mirage.sensor.DefenceState;
import com.mirage.sensor.FakeFtpDecoy;
import com.mirage.sensor.FakeHttpLoginDecoy;
import com.mirage.sensor.FakeTelnetDecoy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-09: "Feed fake data" must be its own response, not a second name for the tarpit. A fed
 * source is let "in" and served believable, worthless content (the same lures that are planted
 * as bait, including the backup.cfg honeytoken), so the attacker keeps going on false data.
 */
class FeedFakeDataTest {

    private static final String SRC = "127.0.0.1";
    private final List<AttackEvent> events = new CopyOnWriteArrayList<>();
    private Decoy decoy;

    @AfterEach
    void stop() {
        if (decoy != null) {
            decoy.stop();
        }
    }

    private static DefenceState fed() {
        DefenceState defence = new DefenceState();
        defence.apply(new ResponseCommand(CommandType.FEED_FAKE, SRC));
        return defence;
    }

    private int start(Decoy d, DefenceState gate) throws IOException {
        decoy = d;
        d.start(events::add, gate);
        return d.boundPort();
    }

    // ---- the Sensor's decision -----------------------------------------------------------

    @Test
    void feedFakeIsItsOwnDecisionNotATarpit() {
        assertEquals(ConnectionGate.Decision.FEED_FAKE, fed().decide(SRC));
        assertTrue(fed().isFedFakeData(SRC));
        assertFalse(fed().isTarpitted(SRC));
    }

    @Test
    void theOperatorsLatestChoiceBetweenTarpitAndFakeDataWins() {
        DefenceState defence = new DefenceState();
        defence.apply(new ResponseCommand(CommandType.TARPIT, SRC));
        defence.apply(new ResponseCommand(CommandType.FEED_FAKE, SRC));
        assertEquals(ConnectionGate.Decision.FEED_FAKE, defence.decide(SRC));
        defence.apply(new ResponseCommand(CommandType.TARPIT, SRC));
        assertEquals(ConnectionGate.Decision.TARPIT, defence.decide(SRC));
    }

    @Test
    void blockBeatsFakeDataAndUnblockClearsIt() {
        DefenceState defence = fed();
        defence.apply(new ResponseCommand(CommandType.BLOCK, SRC));
        assertEquals(ConnectionGate.Decision.BLOCK, defence.decide(SRC));
        defence.apply(new ResponseCommand(CommandType.UNBLOCK, SRC));
        assertEquals(ConnectionGate.Decision.ALLOW, defence.decide(SRC));
    }

    // ---- what each decoy serves a fed source ---------------------------------------------

    @Test
    void httpLetsAnyCredentialInAndServesTheFakeBackupConfig() throws Exception {
        int port = start(new FakeHttpLoginDecoy(0), fed());
        String reply = exchange(port, "GET / HTTP/1.0\r\nAuthorization: Basic "
                + Base64.getEncoder().encodeToString("admin:wrong".getBytes(StandardCharsets.UTF_8))
                + "\r\n\r\n");
        assertTrue(reply.startsWith("HTTP/1.1 200 OK"), reply);
        assertTrue(reply.contains("credentials=svc_backup:S3cr3tBackup!"), reply);
        waitFor(() -> events.stream().anyMatch(e -> e.action() == Action.LOGIN_ATTEMPT
                && "admin:wrong".equals(e.payload())));
        // a wrong password let in by the deception is not a real break-in
        assertTrue(events.stream().noneMatch(e -> e.action() == Action.DECOY_ACCESS), events.toString());
    }

    @Test
    void httpStillAsksForCredentialsFirst() throws Exception {
        int port = start(new FakeHttpLoginDecoy(0), fed());
        String reply = exchange(port, "GET / HTTP/1.0\r\n\r\n");
        assertTrue(reply.startsWith("HTTP/1.1 401"), reply);
    }

    @Test
    void ftpAcceptsTheLoginAndListsTheLures() throws Exception {
        int port = start(new FakeFtpDecoy(0), fed());
        String reply = exchange(port, "USER admin\r\nPASS wrong\r\nPWD\r\nSTAT\r\nQUIT\r\n");
        assertTrue(reply.contains("\r\n230 "), reply);
        assertFalse(reply.contains("530"), reply);
        assertTrue(reply.contains("passwords.csv") && reply.contains("backup.cfg"), reply);
        assertTrue(reply.contains("221 "), reply);
        waitFor(() -> events.stream().anyMatch(e -> e.action() == Action.LOGIN_ATTEMPT
                && "admin:wrong".equals(e.payload())));
    }

    @Test
    void telnetOpensAFakeShellThatShowsTheBackupConfig() throws Exception {
        int port = start(new FakeTelnetDecoy(0), fed());
        String reply = exchange(port, "admin\r\nwrong\r\nwhoami\r\nls\r\ncat config/backup.cfg\r\nexit\r\n");
        assertFalse(reply.contains("Login incorrect"), reply);
        assertTrue(reply.contains("passwords.csv"), reply);
        assertTrue(reply.contains("credentials=svc_backup:S3cr3tBackup!"), reply);
        waitFor(() -> events.stream().anyMatch(e -> e.action() == Action.LOGIN_ATTEMPT
                && "admin:wrong".equals(e.payload())));
    }

    @Test
    void anUnaffectedSourceStillGetsTheNormalRejection() throws Exception {
        DefenceState defence = new DefenceState();
        defence.apply(new ResponseCommand(CommandType.FEED_FAKE, "10.10.0.99"));
        int port = start(new FakeFtpDecoy(0), defence);
        String reply = exchange(port, "USER admin\r\nPASS wrong\r\n");
        assertTrue(reply.contains("530 Login incorrect"), reply);
    }

    /** Send the whole script, then read until the decoy closes (or 3 s pass). */
    private static String exchange(int port, String script) throws IOException {
        try (Socket socket = new Socket(SRC, port)) {
            socket.setSoTimeout(3_000);
            OutputStream out = socket.getOutputStream();
            out.write(script.getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream got = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            try {
                int n;
                while ((n = in.read(buf)) != -1) {
                    got.write(buf, 0, n);
                }
            } catch (SocketTimeoutException e) {
                // a stalled reply: return what arrived, the assertions say what's wrong
            }
            return got.toString(StandardCharsets.UTF_8);
        }
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 3_000;
        while (!condition.getAsBoolean()) {
            assertTrue(System.currentTimeMillis() < end, "condition not met within 3 s");
            Thread.sleep(20);
        }
    }
}
