package com.mirage;

import com.mirage.common.LabConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-32: a mistyped flag must not be silently ignored (the app would run on the default lab
 * range and Specter would refuse the real target with no hint why), and a port must be a port.
 */
class LabConfigFlagsTest {

    @Test
    void theNaturalSpellingOfTheRangeFlagIsAccepted() {
        assertEquals("192.168.0.0/24",
                LabConfig.parseArgs(new String[]{"--lab-cidrs", "192.168.0.0/24"}).get("lab.cidrs"));
    }

    @Test
    void anUnknownFlagIsRefusedAndTheKnownOnesAreListed() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LabConfig.parseArgs(new String[]{"--lab-cidrz", "192.168.0.0/24"}));
        assertTrue(e.getMessage().contains("--lab-cidrz"), e.getMessage());
        assertTrue(e.getMessage().contains("--lab-cidr"), e.getMessage());
    }

    @Test
    void aFlagWithoutAValueIsRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LabConfig.parseArgs(new String[]{"--http-port"}));
        assertTrue(e.getMessage().contains("--http-port"), e.getMessage());
    }

    @Test
    void theBareConsoleHostArgumentStillWorks() {
        assertTrue(LabConfig.parseArgs(new String[]{"10.10.0.2"}).isEmpty(), "a bare first word is not a flag");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "65536", "70000"})
    void aPortOutsideTheTcpRangeIsRefusedNamingTheSetting(String port) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LabConfig.from(Map.of("decoy.http.port", port)));
        assertTrue(e.getMessage().contains("decoy.http.port") && e.getMessage().contains(port), e.getMessage());
    }

    @Test
    void theEdgesOfTheRangeAreFine() {
        assertEquals(1, LabConfig.from(Map.of("console.port", "1")).consolePort());
        assertEquals(65535, LabConfig.from(Map.of("console.port", "65535")).consolePort());
    }
}
