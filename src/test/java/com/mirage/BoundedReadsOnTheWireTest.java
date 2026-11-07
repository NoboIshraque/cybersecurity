package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.JsonCodec;
import com.mirage.common.SensorMessage;
import com.mirage.console.ConsoleServer;
import com.mirage.console.SensorRegistry;
import com.mirage.sensor.Decoy;
import com.mirage.sensor.FakeFtpDecoy;
import com.mirage.sensor.FakeHttpLoginDecoy;
import com.mirage.sensor.FakeTelnetDecoy;
import com.mirage.sensor.SensorClient;
import org.junit.jupiter.api.Test;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-08 / A-01 over real sockets: a line with no end, too many HTTP headers, too many
 * connections or an idle connection must end with the peer being cut off, not with the
 * Sensor or Console buffering, waiting or spawning threads without limit.
 */
class BoundedReadsOnTheWireTest {

    /** Write {@code bytes} without a newline (ignoring a reset once the peer gives up). */
    private static void sendNoNewline(Socket s, byte[] prefix, int bytes) {
        try {
            OutputStream out = s.getOutputStream();
            out.write(prefix);
            byte[] chunk = new byte[8192];
            java.util.Arrays.fill(chunk, (byte) 'A');
            for (int sent = 0; sent < bytes; sent += chunk.length) {
                out.write(chunk);
            }
            out.flush();
        } catch (IOException e) {
            // the peer closed on us: what we want
        }
    }

    /** True if the peer closed (EOF or reset) within the timeout. */
    private static boolean closedByPeer(Socket s, int timeoutMs) throws IOException {
        s.setSoTimeout(timeoutMs);
        InputStream in = s.getInputStream();
        try {
            while (in.read() != -1) {
                // drain
            }
            return true;
        } catch (SocketTimeoutException e) {
            return false;
        } catch (SocketException e) {
            return true;
        }
    }

    private static void withDecoy(Decoy decoy, List<AttackEvent> events, DecoyCheck check) throws Exception {
        decoy.start(events::add);
        try (Socket s = new Socket("127.0.0.1", decoy.boundPort())) {
            check.run(s);
        } finally {
            decoy.stop();
        }
    }

    private interface DecoyCheck {
        void run(Socket s) throws Exception;
    }

    @Test
    void everyDecoyCutsOffALineWithNoEnd() throws Exception {
        List<Function<Integer, Decoy>> decoys = List.of(FakeHttpLoginDecoy::new, FakeFtpDecoy::new,
                FakeTelnetDecoy::new);
        for (Function<Integer, Decoy> make : decoys) {
            List<AttackEvent> events = new CopyOnWriteArrayList<>();
            Decoy decoy = make.apply(0);
            withDecoy(decoy, events, s -> {
                byte[] prefix = decoy instanceof FakeHttpLoginDecoy
                        ? "GET / HTTP/1.0\r\nX: ".getBytes(StandardCharsets.US_ASCII) : new byte[0];
                CompletableFuture<Void> writer = CompletableFuture.runAsync(
                        () -> sendNoNewline(s, prefix, 2 * 1024 * 1024));
                assertTrue(closedByPeer(s, 3000), decoy.type() + " must hang up on an endless line");
                writer.get(5, TimeUnit.SECONDS);
            });
            assertTrue(events.stream().anyMatch(e -> e.payload() != null && e.payload().contains("line longer than")),
                    decoy.type() + " reports the oversized line");
        }
    }

    @Test
    void httpDecoyStopsAfterTooManyHeaderLines() throws Exception {
        withDecoy(new FakeHttpLoginDecoy(0), new CopyOnWriteArrayList<>(), s -> {
            StringBuilder req = new StringBuilder("GET / HTTP/1.0\r\n");
            for (int i = 0; i < 60; i++) {
                req.append("X-").append(i).append(": y\r\n");
            }
            s.getOutputStream().write(req.toString().getBytes(StandardCharsets.US_ASCII)); // no blank line
            s.getOutputStream().flush();
            assertTrue(closedByPeer(s, 3000), "the decoy stops reading after "
                    + FakeHttpLoginDecoy.MAX_HEADER_LINES + " header lines");
        });
    }

    private static ConsoleServer console(BlockingQueue<AttackEvent> queue) {
        return new ConsoleServer(ServerSocketFactory.getDefault(), queue, new SensorRegistry());
    }

    @Test
    void consoleCutsOffAnOversizedLineAndKeepsServingOthers() throws Exception {
        BlockingQueue<AttackEvent> queue = new LinkedBlockingQueue<>();
        ConsoleServer server = console(queue);
        int port = server.start(0);
        try (Socket bad = new Socket("127.0.0.1", port)) {
            CompletableFuture<Void> writer = CompletableFuture.runAsync(
                    () -> sendNoNewline(bad, new byte[0], 4 * ConsoleServer.MAX_LINE_BYTES));
            assertTrue(closedByPeer(bad, 3000), "an oversized line closes that link");
            writer.get(5, TimeUnit.SECONDS);

            try (Socket good = new Socket("127.0.0.1", port)) {
                good.getOutputStream().write((JsonCodec.encode(SensorMessage.event(new AttackEvent(
                        "10.10.0.5", 1, "ftp", Action.CONNECT, "", Instant.now()))) + "\n")
                        .getBytes(StandardCharsets.UTF_8));
                good.getOutputStream().flush();
                assertNotNull(queue.poll(3, TimeUnit.SECONDS), "other Sensors are unaffected");
            }
        } finally {
            server.stop();
        }
    }

    @Test
    void consoleCapsConcurrentConnections() throws Exception {
        ConsoleServer server = console(new LinkedBlockingQueue<>()).setMaxConnections(4);
        int port = server.start(0);
        List<Socket> sockets = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                sockets.add(new Socket("127.0.0.1", port));
            }
            int cutOff = 0;
            for (Socket s : sockets) {
                if (closedByPeer(s, 500)) {
                    cutOff++;
                }
            }
            assertEquals(4, cutOff, "connections past the cap are closed at once");
            assertTrue(server.connectionCount() <= 4);
        } finally {
            for (Socket s : sockets) {
                s.close();
            }
            server.stop();
        }
    }

    @Test
    void consoleClosesALinkThatGoesSilent() throws Exception {
        ConsoleServer server = console(new LinkedBlockingQueue<>()).setIdleTimeout(Duration.ofMillis(300));
        int port = server.start(0);
        try (Socket idle = new Socket("127.0.0.1", port)) {
            assertTrue(closedByPeer(idle, 3000), "no heartbeat within the idle timeout: link closed");
        } finally {
            server.stop();
        }
    }

    @Test
    void sensorCutsOffAnOversizedCommandLine() throws Exception {
        try (ServerSocket fakeConsole = new ServerSocket(0);
             SensorClient client = new SensorClient("s1", SocketFactory.getDefault(), "127.0.0.1",
                     fakeConsole.getLocalPort());
             Socket peer = fakeConsole.accept()) {
            CompletableFuture<Void> receiving = CompletableFuture.runAsync(() -> {
                try {
                    client.receive(c -> { });
                } catch (IOException e) {
                    // expected: line too long
                }
            });
            CompletableFuture.runAsync(() -> sendNoNewline(peer, new byte[0], 4 * ConsoleServer.MAX_LINE_BYTES));
            receiving.get(3, TimeUnit.SECONDS); // returns instead of buffering forever
        }
    }
}
