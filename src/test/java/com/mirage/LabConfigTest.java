package com.mirage;

import com.mirage.common.LabConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LabConfigTest {

    @Test
    void defaultsReproduceSingleMachineSetup() {
        LabConfig cfg = LabConfig.defaults();
        assertEquals("127.0.0.1", cfg.consoleHost());
        assertEquals(8443, cfg.consolePort());
        assertEquals(8081, cfg.httpDecoyPort());
        assertEquals(8080, cfg.counterWatchPort());
        assertTrue(cfg.labCidrs().contains("127.0.0.1/32"));
        assertDoesNotThrow(cfg::scopeGuard);
    }

    @Test
    void cliFlagsOverrideDefaults() {
        String[] args = {"--console-host=192.168.0.107", "--lab-cidr=192.168.0.0/24", "--console-port", "9443"};
        LabConfig cfg = LabConfig.load(null, args);
        assertEquals("192.168.0.107", cfg.consoleHost());
        assertEquals(9443, cfg.consolePort());
        assertEquals(List.of("192.168.0.0/24"), cfg.labCidrs());
        assertTrue(cfg.scopeGuard().isAuthorised("192.168.0.50"));
        assertFalse(cfg.scopeGuard().isAuthorised("10.0.0.1"));
    }

    @Test
    void parseArgsSupportsEqualsAndSpaceForms() {
        Map<String, String> parsed = LabConfig.parseArgs(
                new String[]{"--console-host", "10.10.0.2", "--http-port=9090", "ignored", "--bogus=x"});
        assertEquals("10.10.0.2", parsed.get("console.host"));
        assertEquals("9090", parsed.get("decoy.http.port"));
        assertFalse(parsed.containsKey("bogus"));
    }

    @Test
    void multipleCidrsSplitAndTrim() {
        LabConfig cfg = LabConfig.from(Map.of("lab.cidrs", "10.10.0.0/24, 192.168.0.0/24 "));
        assertEquals(List.of("10.10.0.0/24", "192.168.0.0/24"), cfg.labCidrs());
    }

    @Test
    void unsafeConfiguredRangeIsRefusedWhenBuildingGuard() {
        LabConfig publicRange = LabConfig.from(Map.of("lab.cidrs", "8.8.8.0/24"));
        assertThrows(IllegalArgumentException.class, publicRange::scopeGuard);
    }
}
