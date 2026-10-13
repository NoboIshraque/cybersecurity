package com.mirage;

import com.mirage.common.AttackEvent;
import com.mirage.sensor.ConnectionGate;
import com.mirage.sensor.FakeFtpDecoy;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BlockedDecoyTest {

    @Test
    void blockedSourceIsDroppedWithoutABanner() throws Exception {
        BlockingQueue<AttackEvent> events = new LinkedBlockingQueue<>();
        FakeFtpDecoy decoy = new FakeFtpDecoy(0);
        ConnectionGate blockLoopback = ip ->
                ip.equals("127.0.0.1") ? ConnectionGate.Decision.BLOCK : ConnectionGate.Decision.ALLOW;
        decoy.start(events::add, blockLoopback);

        try (Socket s = new Socket("127.0.0.1", decoy.boundPort())) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            assertNull(in.readLine(), "a blocked source should be dropped, not greeted with a banner");

            AttackEvent event = events.poll(3, TimeUnit.SECONDS);
            assertNotNull(event);
            assertEquals("blocked", event.payload());
        } finally {
            decoy.stop();
        }
    }
}
