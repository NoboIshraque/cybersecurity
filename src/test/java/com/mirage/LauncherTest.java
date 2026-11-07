package com.mirage;

import com.mirage.common.LabConfig;
import org.junit.jupiter.api.Test;

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
}
