package com.mirage;

import com.mirage.console.ConsoleServer;
import com.mirage.console.SensorRegistry;
import com.mirage.sensor.SensorMain;
import org.junit.jupiter.api.Test;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-31: with a decoy port already taken, the Sensor used to connect to the Console first and
 * then die on the bind, leaving a "connected" Sensor with no decoys (the dashboard counted it,
 * and no heartbeat meant no dead-sensor warning either).
 */
class SensorStartupTest {

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @Test
    void aTakenDecoyPortStopsTheSensorBeforeItConnects() throws Exception {
        ConsoleServer console = new ConsoleServer(ServerSocketFactory.getDefault(),
                new LinkedBlockingQueue<>(), new SensorRegistry());
        int consolePort = console.start(0);
        int http = freePort();
        int ftp = freePort();
        try (ServerSocket taken = new ServerSocket(0)) { // the telnet port: bound last
            String[] args = {"--console-port=" + consolePort, "--http-port=" + http,
                    "--ftp-port=" + ftp, "--telnet-port=" + taken.getLocalPort()};

            BindException e = assertThrows(BindException.class, () -> SensorMain.start(args));
            Thread.sleep(500);
            assertEquals(0, console.connectionCount(), "no link to the Console was left open");
            assertTrue(e.getMessage().contains(String.valueOf(taken.getLocalPort())),
                    "the message names the taken port: " + e.getMessage());
            // the decoys that did bind were released again
            new ServerSocket(http).close();
            new ServerSocket(ftp).close();
        } finally {
            console.stop();
        }
    }
}
