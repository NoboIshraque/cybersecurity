package com.mirage;

import com.mirage.sensor.SensorMain;
import com.mirage.specter.SpecterMain;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FB-08: a refused start-up must be visible and must fail. The attacker and Sensor printed
 * "Refusing to start" and then exited with code 0, so a script could not tell a refusal from a
 * normal start. A double-clicked run-*.bat closed its window at once, so nobody saw the message.
 */
class StartupRefusalTest {

    @Test
    void theAttackerExitsWithAnErrorWhenItRefusesToStart() {
        assertEquals(1, SpecterMain.launch(new String[]{"--lab-cidr=8.8.8.0/24"}), "a public lab range");
        assertEquals(1, SpecterMain.launch(new String[]{"--no-such-flag=1"}), "an unknown flag");
    }

    @Test
    void theSensorExitsWithAnErrorWhenItRefusesToStart() throws Exception {
        assertEquals(1, SensorMain.launch(new String[]{"--no-such-flag=1"}), "an unknown flag");
        assertEquals(1, SensorMain.launch(new String[]{"--http-port=70000"}), "an impossible port");
    }

    /** Both Windows scripts keep their window open and pass the exit code on when Java fails. */
    @Test
    void theWindowsScriptsPauseAndKeepTheExitCodeWhenJavaFails() throws Exception {
        for (String script : new String[]{"run-defender.bat", "run-attacker.bat"}) {
            String text = Files.readString(Path.of(script), StandardCharsets.UTF_8).toLowerCase();
            int java = text.indexOf("java -jar");
            assertTrue(java >= 0, script);
            String after = text.substring(java);
            assertTrue(after.contains("set \"code=%errorlevel%\""), script + " saves Java's exit code");
            assertTrue(after.contains("pause"), script + " keeps the window open on a failure");
            assertTrue(after.contains("exit /b %code%"), script + " exits with Java's code");
        }
    }
}
