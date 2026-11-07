package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.CommandType;
import com.mirage.common.JsonCodec;
import com.mirage.common.ResponseCommand;
import com.mirage.common.SensorMessage;
import com.mirage.console.ConsoleServer;
import com.mirage.console.SensorRegistry;
import com.mirage.sensor.SensorClient;
import org.junit.jupiter.api.Test;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-13: one unparsable line, a heartbeat with no timestamp, or a JSON field from a newer build
 * used to make the Console close the Sensor's link. Each bad line is now skipped and the link
 * stays up; the Sensor side does the same for commands.
 */
class WireToleranceTest {

    @Test
    void decodeIgnoresUnknownFields() {
        SensorMessage m = JsonCodec.decode(
                "{\"type\":\"HEARTBEAT\",\"sensorId\":\"s1\",\"timestamp\":\"2026-11-07T10:00:00Z\",\"version\":2}");
        assertEquals("s1", m.sensorId());
        ResponseCommand c = JsonCodec.decodeCommand("{\"type\":\"BLOCK\",\"targetIp\":\"10.10.0.5\",\"ttl\":30}");
        assertEquals(CommandType.BLOCK, c.type());
    }

    @Test
    void consoleKeepsTheLinkThroughBadLines() throws Exception {
        BlockingQueue<AttackEvent> queue = new LinkedBlockingQueue<>();
        SensorRegistry registry = new SensorRegistry();
        ConsoleServer server = new ConsoleServer(ServerSocketFactory.getDefault(), queue, registry);
        int port = server.start(0);
        try (Socket s = new Socket("127.0.0.1", port);
             Writer out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8)) {
            String ts = Instant.now().toString();
            out.write("hello not json\n");
            out.write("{\"type\":\"HEARTBEAT\",\"sensorId\":\"s-raw\"}\n"); // no timestamp
            out.write("{\"type\":\"EVENT\",\"version\":2,\"event\":{\"sourceIp\":\"10.10.0.77\",\"sourcePort\":1,"
                    + "\"decoyType\":\"ftp\",\"action\":\"CONNECT\",\"payload\":\"\",\"timestamp\":\"" + ts
                    + "\",\"newField\":true}}\n"); // fields from a newer build
            out.write(JsonCodec.encode(SensorMessage.event(
                    new AttackEvent("10.10.0.78", 2, "ftp", Action.CONNECT, "", Instant.now()))) + "\n");
            out.flush();

            AttackEvent first = queue.poll(3, TimeUnit.SECONDS);
            assertNotNull(first, "the newer-build event is accepted");
            assertEquals("10.10.0.77", first.sourceIp());
            AttackEvent second = queue.poll(3, TimeUnit.SECONDS);
            assertNotNull(second, "the link survived the bad lines");
            assertEquals("10.10.0.78", second.sourceIp());
            assertTrue(registry.isKnown("s-raw"), "a heartbeat with no timestamp still registers");
            assertEquals(1, server.connectionCount());
        } finally {
            server.stop();
        }
    }

    @Test
    void sensorKeepsReceivingAfterABadCommandLine() throws Exception {
        BlockingQueue<ResponseCommand> got = new LinkedBlockingQueue<>();
        try (ServerSocket fakeConsole = new ServerSocket(0);
             SensorClient client = new SensorClient("s1", SocketFactory.getDefault(), "127.0.0.1",
                     fakeConsole.getLocalPort());
             Socket peer = fakeConsole.accept();
             Writer out = new OutputStreamWriter(peer.getOutputStream(), StandardCharsets.UTF_8)) {
            client.startReceiving(got::add);
            out.write("garbage\n");
            out.write("{\"type\":\"NUKE\",\"targetIp\":\"10.10.0.5\"}\n"); // unknown command type
            out.write(JsonCodec.encode(new ResponseCommand(CommandType.BLOCK, "10.10.0.5")) + "\n");
            out.flush();
            ResponseCommand cmd = got.poll(3, TimeUnit.SECONDS);
            assertNotNull(cmd, "the valid command after the bad ones is applied");
            assertEquals(CommandType.BLOCK, cmd.type());
            assertNull(got.poll(100, TimeUnit.MILLISECONDS));
        }
    }
}
