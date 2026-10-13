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

/** The Console tags each event with the sensorId of the connection it arrived on. */
class SensorTaggingTest {

    @Test
    void eventsAreTaggedWithTheReportingSensor() throws Exception {
        BlockingQueue<AttackEvent> queue = new ArrayBlockingQueue<>(16);
        SensorRegistry registry = new SensorRegistry();
        ConsoleServer server = new ConsoleServer(ServerSocketFactory.getDefault(), queue, registry);
        int port = server.start(0);

        try (SensorClient client =
                     new SensorClient("sensor-x", SocketFactory.getDefault(), "127.0.0.1", port)) {
            client.sendHeartbeat(); // identifies the connection
            client.sendEvent(new AttackEvent("10.10.0.5", 1, "ftp", Action.CONNECT, "", Instant.now()));

            AttackEvent received = queue.poll(3, TimeUnit.SECONDS);
            assertNotNull(received);
            assertEquals("sensor-x", received.sensorId());
        } finally {
            server.stop();
        }
    }
}
