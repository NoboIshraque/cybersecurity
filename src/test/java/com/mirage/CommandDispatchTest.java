package com.mirage;

import com.mirage.common.AttackEvent;
import com.mirage.common.CommandType;
import com.mirage.common.ResponseCommand;
import com.mirage.console.ConsoleServer;
import com.mirage.console.SensorRegistry;
import com.mirage.sensor.SensorClient;
import org.junit.jupiter.api.Test;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CommandDispatchTest {

    @Test
    void consoleBroadcastReachesSensor() throws Exception {
        BlockingQueue<AttackEvent> queue = new ArrayBlockingQueue<>(16);
        SensorRegistry registry = new SensorRegistry();
        ConsoleServer server = new ConsoleServer(ServerSocketFactory.getDefault(), queue, registry);
        int port = server.start(0);

        BlockingQueue<ResponseCommand> received = new LinkedBlockingQueue<>();
        try (SensorClient client =
                     new SensorClient("sensor-a", SocketFactory.getDefault(), "127.0.0.1", port)) {
            client.startReceiving(received::add);
            client.sendHeartbeat();

            long deadline = System.currentTimeMillis() + 3000;
            while (server.connectionCount() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(server.connectionCount() >= 1, "the server should have a live connection");

            server.broadcast(new ResponseCommand(CommandType.BLOCK, "10.10.0.5"));

            ResponseCommand cmd = received.poll(3, TimeUnit.SECONDS);
            assertNotNull(cmd, "the sensor should receive the broadcast command");
            assertEquals(CommandType.BLOCK, cmd.type());
            assertEquals("10.10.0.5", cmd.targetIp());
        } finally {
            server.stop();
        }
    }
}
