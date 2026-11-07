package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.JsonCodec;
import com.mirage.common.SensorMessage;
import com.mirage.console.ConsoleServer;
import com.mirage.console.EventValidator;
import com.mirage.console.SensorRegistry;
import org.junit.jupiter.api.Test;

import javax.net.ServerSocketFactory;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-01: the Console checks each incoming event before it reaches the engine, so a Sensor
 * (or anyone on the Console port) can't queue an event the pipeline can't handle.
 */
class ConsoleServerEventValidationTest {

    private static AttackEvent ok() {
        return new AttackEvent("10.10.0.77", 4000, "ftp", Action.LOGIN_ATTEMPT, "a:b", Instant.now());
    }

    @Test
    void validatorRejectsMissingOrOversizedFields() {
        assertTrue(EventValidator.problem(ok()).isEmpty());
        AttackEvent e = ok();
        assertTrue(EventValidator.problem(new AttackEvent(null, 1, "ftp", e.action(), "", e.timestamp())).isPresent());
        assertTrue(EventValidator.problem(new AttackEvent(" ", 1, "ftp", e.action(), "", e.timestamp())).isPresent());
        assertTrue(EventValidator.problem(new AttackEvent("10.10.0.77", 1, "ftp", null, "", e.timestamp())).isPresent());
        assertTrue(EventValidator.problem(new AttackEvent("10.10.0.77", 1, "ftp", e.action(), "", null)).isPresent());
        assertTrue(EventValidator.problem(new AttackEvent("10.10.0.77", 1, null, e.action(), "", e.timestamp())).isPresent());
        assertTrue(EventValidator.problem(new AttackEvent("1".repeat(60), 1, "ftp", e.action(), "", e.timestamp())).isPresent(),
                "longer than the SRC_IP VARCHAR(45) column");
        assertTrue(EventValidator.problem(new AttackEvent("10.10.0.77", 1, "x".repeat(33), e.action(), "", e.timestamp())).isPresent());
    }

    @Test
    void normaliseFillsAMissingPayloadAndCapsALongOne() {
        AttackEvent e = ok();
        assertEquals("", EventValidator.normalise(new AttackEvent("10.10.0.77", 1, "ftp", e.action(), null, e.timestamp())).payload());
        String huge = "p".repeat(EventValidator.MAX_PAYLOAD + 500);
        assertEquals(EventValidator.MAX_PAYLOAD,
                EventValidator.normalise(new AttackEvent("10.10.0.77", 1, "ftp", e.action(), huge, e.timestamp())).payload().length());
    }

    /** The live repro from B-01 (d): an EVENT with no "action" used to reach the engine and kill it. */
    @Test
    void invalidEventsOverTheWireAreDroppedBeforeTheQueue() throws Exception {
        BlockingQueue<AttackEvent> queue = new LinkedBlockingQueue<>();
        ConsoleServer server = new ConsoleServer(ServerSocketFactory.getDefault(), queue, new SensorRegistry());
        int port = server.start(0);
        try (Socket s = new Socket("127.0.0.1", port);
             Writer out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8)) {
            String ts = Instant.now().toString();
            out.write("{\"type\":\"EVENT\",\"event\":{\"sourceIp\":\"10.10.0.77\",\"decoyType\":\"ftp\","
                    + "\"payload\":\"x\",\"timestamp\":\"" + ts + "\"}}\n");                  // no action
            out.write("{\"type\":\"EVENT\",\"event\":{\"sourceIp\":\"" + "1".repeat(60) + "\",\"decoyType\":\"ftp\","
                    + "\"action\":\"CONNECT\",\"payload\":\"\",\"timestamp\":\"" + ts + "\"}}\n"); // too long
            out.write(JsonCodec.encode(SensorMessage.event(ok())) + "\n");                       // valid
            out.flush();

            AttackEvent first = queue.poll(3, TimeUnit.SECONDS);
            assertNotNull(first, "the valid event still arrives (the link is not dropped)");
            assertEquals(Action.LOGIN_ATTEMPT, first.action(), "the invalid events never reached the queue");
            assertNull(queue.poll(200, TimeUnit.MILLISECONDS));
        } finally {
            server.stop();
        }
    }
}
