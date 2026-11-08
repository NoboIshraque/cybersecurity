package com.mirage;

import com.mirage.common.LabConfig;
import org.junit.jupiter.api.Test;

import java.net.BindException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** The defender's in-process Sensor always reports to its own Console. */
class LauncherTest {

    @Test
    void defenderSensorAlwaysReportsToLocalConsole() {
        String[] user = {"--console-host=192.168.0.107", "--lab-cidr", "192.168.0.0/24"};
        String[] sensor = Launcher.sensorArgs(user);

        LabConfig cfg = LabConfig.load(null, sensor);
        assertEquals("127.0.0.1", cfg.consoleHost(), "the last --console-host wins");
        assertEquals("192.168.0.0/24", cfg.labCidrs().get(0), "other flags pass through");
        assertTrue(sensor[0].startsWith("--"), "a bare first arg would be read as the console host");
    }

    /**
     * B-10: a leftover Console already holds the port. The defender used to see "something is
     * listening", start its Sensor against that other process, and keep running without a
     * Console of its own.
     */
    @Test
    void defenderStopsWhenItsOwnConsoleCannotBind() throws Exception {
        try (ServerSocket leftover = new ServerSocket(0)) {
            AtomicBoolean sensorStarted = new AtomicBoolean();
            Launcher.Step console = a -> {
                Thread.sleep(300); // a real Console spends a while on keys and H2 before it binds
                throw new BindException("Address already in use: bind");
            };
            int code = Launcher.defender(new String[]{"--console-port=" + leftover.getLocalPort()},
                    console, a -> sensorStarted.set(true));
            assertNotEquals(0, code, "the defender must fail");
            assertFalse(sensorStarted.get(), "no Sensor may start against someone else's Console");
        }
    }

    /** B-31: the Sensor half failing (e.g. a decoy port taken) fails the whole role. */
    @Test
    void defenderFailsWhenItsSensorCannotStart() throws Exception {
        try (ServerSocket console = new ServerSocket(0)) {
            int code = assertDoesNotThrow(() -> Launcher.defender(
                    new String[]{"--console-port=" + console.getLocalPort()},
                    a -> { }, // the Console is up
                    a -> {
                        throw new BindException("Address already in use: bind");
                    }));
            assertNotEquals(0, code);
        }
    }
}
