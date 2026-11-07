package com.mirage;

import com.mirage.common.AttackEvent;
import com.mirage.sensor.ConnectionGate;
import com.mirage.sensor.Decoy;
import com.mirage.sensor.DecoyLimits;
import com.mirage.sensor.FakeFtpDecoy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-06 / B-07 / A-04: twelve idle or tarpitted connections used to take every worker of a
 * decoy, so nobody else was served and their attacks went unseen. The attacker here is
 * 127.0.0.1; the bystander who must still be served is 127.0.0.2.
 */
class DecoyStarvationTest {

    private static final String ATTACKER = "127.0.0.1";
    private static final String BYSTANDER = "127.0.0.2";

    private final List<Socket> sockets = new ArrayList<>();
    private final List<AttackEvent> events = new CopyOnWriteArrayList<>();
    private Decoy decoy;

    @AfterEach
    void tearDown() throws IOException {
        for (Socket s : sockets) {
            s.close();
        }
        if (decoy != null) {
            decoy.stop();
        }
    }

    private int start(DecoyLimits limits, ConnectionGate gate) throws IOException {
        decoy = new FakeFtpDecoy(0).limits(limits);
        decoy.start(events::add, gate);
        return decoy.boundPort();
    }

    private Socket connectFrom(String source, int port) throws IOException {
        Socket s = new Socket();
        s.bind(new InetSocketAddress(InetAddress.getByName(source), 0));
        s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
        sockets.add(s);
        return s;
    }

    /** Reads the FTP banner line, or fails if it doesn't come within the timeout. */
    private static String banner(Socket s, int timeoutMs) throws IOException {
        s.setSoTimeout(timeoutMs);
        InputStream in = s.getInputStream();
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1 && c != '\n') {
            sb.append((char) c);
        }
        return sb.toString().trim();
    }

    /** True if the decoy closed (EOF or reset) within the timeout. */
    private static boolean closedByDecoy(Socket s, int timeoutMs) throws IOException {
        s.setSoTimeout(timeoutMs);
        InputStream in = s.getInputStream();
        try {
            while (in.read() != -1) {
                // drain banner / tarpit dots
            }
            return true;
        } catch (SocketTimeoutException e) {
            return false;
        } catch (SocketException e) {
            return true; // reset
        }
    }

    private static DecoyLimits limits(Duration readTimeout, int perSource, Duration hold) {
        return new DecoyLimits(readTimeout, perSource, 12, 64, Duration.ofMillis(100), hold, 64);
    }

    @Test
    void anIdleClientIsHungUpOnAfterTheReadTimeout() throws IOException {
        int port = start(limits(Duration.ofMillis(300), 8, Duration.ofMinutes(2)), ConnectionGate.ALLOW_ALL);
        Socket idle = connectFrom(ATTACKER, port);
        assertTrue(closedByDecoy(idle, 3000), "an idle client must not hold a worker forever");
    }

    @Test
    void oneSourceCannotTakeEveryWorker() throws IOException {
        // long read timeout, so only the per-source cap can save the bystander
        int port = start(limits(Duration.ofMinutes(1), 4, Duration.ofMinutes(2)), ConnectionGate.ALLOW_ALL);
        for (int i = 0; i < 12; i++) {
            connectFrom(ATTACKER, port);
        }
        Socket bystander = connectFrom(BYSTANDER, port);
        assertTrue(banner(bystander, 3000).startsWith("220"), "the bystander is still served");
        assertTrue(events.stream().anyMatch(e -> e.sourceIp().equals(ATTACKER)
                        && e.payload().contains("limit")),
                "connections over the cap are reported, not silently ignored");
    }

    @Test
    void tarpittedConnectionsDoNotStarveOtherSources() throws IOException {
        // per-source cap high: only moving tarpits off the worker pool can save the bystander
        ConnectionGate gate = src -> src.equals(ATTACKER) ? ConnectionGate.Decision.TARPIT
                : ConnectionGate.Decision.ALLOW;
        int port = start(limits(Duration.ofMinutes(1), 20, Duration.ofMinutes(2)), gate);
        List<Socket> held = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            held.add(connectFrom(ATTACKER, port));
        }
        assertEquals('.', held.get(0).getInputStream().read(), "the tarpit still drips");
        Socket bystander = connectFrom(BYSTANDER, port);
        assertTrue(banner(bystander, 3000).startsWith("220"), "the bystander is still served");
    }

    @Test
    void aTarpitLetsGoAfterItsMaximumHold() throws IOException {
        ConnectionGate gate = src -> ConnectionGate.Decision.TARPIT;
        int port = start(limits(Duration.ofMinutes(1), 8, Duration.ofMillis(500)), gate);
        Socket held = connectFrom(ATTACKER, port);
        assertTrue(closedByDecoy(held, 3000), "the tarpit closes after its maximum hold time");
    }
}
