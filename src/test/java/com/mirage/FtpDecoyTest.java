package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.sensor.FakeFtpDecoy;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FtpDecoyTest {

    @Test
    void capturesLoginAttempt() throws Exception {
        BlockingQueue<AttackEvent> events = new LinkedBlockingQueue<>();
        FakeFtpDecoy decoy = new FakeFtpDecoy(0); // ephemeral port
        decoy.start(events::add);
        int port = decoy.boundPort();

        try (Socket s = new Socket("127.0.0.1", port)) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = s.getOutputStream();

            assertTrue(in.readLine().startsWith("220"), "should send a banner");
            out.write("USER admin\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            in.readLine(); // 331
            out.write("PASS hunter2\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            in.readLine(); // 530

            AttackEvent login = null;
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline) {
                AttackEvent e = events.poll(500, TimeUnit.MILLISECONDS);
                if (e != null && e.action() == Action.LOGIN_ATTEMPT) {
                    login = e;
                    break;
                }
            }
            assertNotNull(login, "a LOGIN_ATTEMPT should be captured");
            assertEquals("admin:hunter2", login.payload());
            assertEquals("ftp", login.decoyType());
        } finally {
            decoy.stop();
        }
    }
}
