package com.mirage;

import com.mirage.common.CommandType;
import com.mirage.common.ResponseCommand;
import com.mirage.console.ConsoleServer;
import com.mirage.console.SensorRegistry;
import com.mirage.sensor.SensorClient;
import org.junit.jupiter.api.Test;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-15: broadcast wrote to each Sensor in turn, on the caller's thread, with no timeout. One
 * Sensor that stopped reading (laptop asleep, process suspended, Wi-Fi gone without a reset)
 * blocked it for good: the engine stopped detecting, the operator's buttons hung, and healthy
 * Sensors got no more commands.
 */
class StuckSensorTest {

    private static final int COMMANDS = 150;

    /** A big target so a few commands fill the stuck peer's TCP buffers (real ones are small). */
    private static ResponseCommand command(int i) {
        return new ResponseCommand(CommandType.BLOCK, "10.10.0." + (i % 250) + "#" + "x".repeat(8000));
    }

    @Test
    void aSensorThatStopsReadingCannotStallTheConsole() throws Exception {
        ConsoleServer server = new ConsoleServer(ServerSocketFactory.getDefault(), new LinkedBlockingQueue<>(),
                new SensorRegistry()).setWriteTimeout(Duration.ofMillis(500));
        int port = server.start(0);
        Socket stuck = new Socket();
        stuck.setReceiveBufferSize(1024); // never reads a byte
        stuck.connect(new InetSocketAddress("127.0.0.1", port));
        AtomicInteger healthyGot = new AtomicInteger();
        try (SensorClient healthy = new SensorClient("healthy", SocketFactory.getDefault(), "127.0.0.1", port)) {
            healthy.startReceiving(c -> healthyGot.incrementAndGet());
            long deadline = System.currentTimeMillis() + 3000;
            while (server.connectionCount() < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(2, server.connectionCount());

            // Before the fix this loop never finished: it blocked inside the stuck peer's write.
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                for (int i = 0; i < COMMANDS; i++) {
                    server.broadcast(command(i));
                }
            }, "broadcast must not block on a Sensor that stopped reading");

            deadline = System.currentTimeMillis() + 5000;
            while ((healthyGot.get() < COMMANDS || server.connectionCount() > 1) && System.currentTimeMillis() < deadline) {
                Thread.sleep(25);
            }
            assertEquals(COMMANDS, healthyGot.get(), "the healthy Sensor still gets every command");
            assertEquals(1, server.connectionCount(), "the stuck link is dropped (its Sensor will reconnect)");
        } finally {
            stuck.close();
            server.stop();
        }
    }

    /** With the default 10 s write timeout, only a full outbox can drop this link within 3 s. */
    @Test
    void aSensorThatFallsFarBehindIsDroppedWhenItsOutboxFills() throws Exception {
        ConsoleServer server = new ConsoleServer(ServerSocketFactory.getDefault(), new LinkedBlockingQueue<>(),
                new SensorRegistry());
        int port = server.start(0);
        try (Socket stuck = new Socket()) {
            stuck.setReceiveBufferSize(1024);
            stuck.connect(new InetSocketAddress("127.0.0.1", port));
            long deadline = System.currentTimeMillis() + 3000;
            while (server.connectionCount() < 1 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                for (int i = 0; i < 600; i++) {
                    server.broadcast(command(i));
                }
            });
            deadline = System.currentTimeMillis() + 3000;
            while (server.connectionCount() > 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(0, server.connectionCount(), "dropped once its outbox overflowed, well before the timeout");
        } finally {
            server.stop();
        }
    }
}
