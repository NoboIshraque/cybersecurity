package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.CommandType;
import com.mirage.common.ResponseCommand;
import com.mirage.console.ConsoleServer;
import com.mirage.console.SensorRegistry;
import com.mirage.sensor.SensorLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-05: the Sensor's link to the Console used to be opened once at start-up. A Console that
 * started second, or restarted, never heard from the Sensor again. {@link SensorLink} keeps
 * reconnecting, re-registers with a heartbeat, and buffers a bounded number of events while down.
 */
class SensorLinkTest {

    private static final Duration FAST = Duration.ofMillis(50);

    private final List<AutoCloseable> cleanup = new ArrayList<>();
    private final BlockingQueue<ResponseCommand> commands = new LinkedBlockingQueue<>();

    @AfterEach
    void tearDown() throws Exception {
        for (AutoCloseable c : cleanup) {
            c.close();
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static AttackEvent event(int id) {
        return new AttackEvent("10.10.0.5", id, "ftp", Action.CONNECT, "", Instant.now());
    }

    private record Console(ConsoleServer server, BlockingQueue<AttackEvent> queue, SensorRegistry registry) {
    }

    private Console console(int port) throws IOException {
        BlockingQueue<AttackEvent> queue = new LinkedBlockingQueue<>();
        SensorRegistry registry = new SensorRegistry();
        ConsoleServer server = new ConsoleServer(ServerSocketFactory.getDefault(), queue, registry);
        server.start(port);
        cleanup.add(server::stop);
        return new Console(server, queue, registry);
    }

    private SensorLink link(int port, int buffer) {
        SensorLink link = new SensorLink("sensor-r", SocketFactory.getDefault(), "127.0.0.1", port,
                commands::add, FAST, Duration.ofMillis(200), buffer);
        link.start();
        cleanup.add(link);
        return link;
    }

    private static void await(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("timed out waiting for: " + what);
            }
            Thread.sleep(20);
        }
    }

    @Test
    void consoleStartedAfterTheSensorStillHearsFromIt() throws Exception {
        int port = freePort();
        SensorLink link = link(port, 100);
        link.sendEvent(event(1)); // Console not up yet: buffered
        Thread.sleep(200);
        assertFalse(link.isConnected());

        Console console = console(port);
        await(link::isConnected, "link up once the Console is listening");
        await(() -> console.registry().isKnown("sensor-r"), "re-registered with a heartbeat first");
        AttackEvent buffered = console.queue().poll(3, TimeUnit.SECONDS);
        assertNotNull(buffered, "the event sent while down is delivered after connecting");
        assertEquals(1, buffered.sourcePort());
    }

    @Test
    void reconnectsAfterAConsoleRestart() throws Exception {
        int port = freePort();
        Console first = console(port);
        SensorLink link = link(port, 100);
        await(link::isConnected, "first link");
        link.sendEvent(event(1));
        assertNotNull(first.queue().poll(3, TimeUnit.SECONDS));

        first.server().stop(); // the Console goes away...
        await(() -> !link.isConnected(), "link noticed as down");
        Console second = console(port); // ...and comes back on the same port
        await(link::isConnected, "link back up");
        await(() -> second.registry().isKnown("sensor-r"), "registered with the new Console");

        link.sendEvent(event(2));
        AttackEvent after = second.queue().poll(3, TimeUnit.SECONDS);
        assertNotNull(after, "events reach the restarted Console");
        assertEquals(2, after.sourcePort());

        // and commands from the new Console reach the Sensor
        second.server().broadcast(new ResponseCommand(CommandType.BLOCK, "10.10.0.5"));
        ResponseCommand cmd = commands.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmd, "the new link also receives commands");
        assertEquals("10.10.0.5", cmd.targetIp());
    }

    @Test
    void bufferWhileDownIsBoundedAndKeepsTheNewestEvents() throws Exception {
        int port = freePort();
        SensorLink link = link(port, 3);
        for (int i = 1; i <= 5; i++) {
            link.sendEvent(event(i));
        }
        assertEquals(2, link.droppedCount(), "two oldest events dropped past the cap of 3");

        Console console = console(port);
        List<Integer> got = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            AttackEvent e = console.queue().poll(3, TimeUnit.SECONDS);
            assertNotNull(e);
            got.add(e.sourcePort());
        }
        assertEquals(List.of(3, 4, 5), got, "newest three, in order");
        assertNull(console.queue().poll(200, TimeUnit.MILLISECONDS));
    }
}
