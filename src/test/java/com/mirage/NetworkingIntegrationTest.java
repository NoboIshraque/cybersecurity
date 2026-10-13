package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.console.ConsoleServer;
import com.mirage.console.SensorRegistry;
import com.mirage.sensor.SensorClient;
import org.junit.jupiter.api.Test;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NetworkingIntegrationTest {

    @Test
    void sensorEventReachesConsoleAndHeartbeatRegisters() throws Exception {
        BlockingQueue<AttackEvent> queue = new ArrayBlockingQueue<>(16);
        SensorRegistry registry = new SensorRegistry();
        ConsoleServer server = new ConsoleServer(ServerSocketFactory.getDefault(), queue, registry);
        int port = server.start(0); // ephemeral loopback port

        try (SensorClient client =
                     new SensorClient("sensor-a", SocketFactory.getDefault(), "127.0.0.1", port)) {
            client.sendHeartbeat();
            client.sendEvent(new AttackEvent("10.10.0.5", 5555, "http-login",
                    Action.CONNECT, "", Instant.now()));

            AttackEvent received = queue.poll(3, TimeUnit.SECONDS);
            assertNotNull(received, "event should reach the console queue");
            assertEquals("10.10.0.5", received.sourceIp());

            long deadline = System.currentTimeMillis() + 3000;
            while (!registry.isKnown("sensor-a") && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(registry.isKnown("sensor-a"), "heartbeat should register the sensor");
        } finally {
            server.stop();
        }
    }
}
