package com.mirage;

import com.mirage.common.LabConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** B-22: {@code .properties} has no inline comments, so "value   # note" must not leak the note. */
    @Test
    void inlineHashCommentsInThePropertiesFileAreStripped(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("mirage.properties");
        Files.writeString(file, String.join("\n",
                "lab.cidrs=192.168.0.0/24,127.0.0.1/32     # your network's range",
                "console.host=192.168.0.107                # Laptop A's IP",
                "console.port=9443\t# tab before the comment",
                ""));
        LabConfig cfg = LabConfig.load(file, new String[0]);
        assertEquals(List.of("192.168.0.0/24", "127.0.0.1/32"), cfg.labCidrs());
        assertEquals("192.168.0.107", cfg.consoleHost());
        assertEquals(9443, cfg.consolePort());
        assertDoesNotThrow(cfg::scopeGuard);
    }

    /** B-22: the snippet users copy from the two-laptop guide must load as-is. */
    @Test
    void twoLaptopGuideSnippetLoadsCleanly(@TempDir Path dir) throws IOException {
        String doc = Files.readString(Path.of("docs", "TWO-LAPTOP-SETUP.md"));
        Matcher m = Pattern.compile("```properties\\R(.*?)```", Pattern.DOTALL).matcher(doc);
        assertTrue(m.find(), "the guide has a ```properties block");
        String snippet = m.group(1);
        for (String line : snippet.split("\\R")) {
            String t = line.strip();
            if (!t.isEmpty() && !t.startsWith("#")) {
                assertFalse(t.contains("#"), "inline comment in the guide's snippet: " + line);
            }
        }
        Path file = dir.resolve("mirage.properties");
        Files.writeString(file, snippet);
        LabConfig cfg = LabConfig.load(file, new String[0]);
        assertEquals("192.168.0.107", cfg.consoleHost());
        assertDoesNotThrow(cfg::scopeGuard);
    }

    @Test
    void lanIpPrefersTheAddressInsideTheLabRange() {
        // e.g. a VirtualBox host-only adapter listed before the Wi-Fi adapter
        List<String> candidates = List.of("192.168.56.1", "192.168.0.107");
        var guard = LabConfig.from(Map.of("lab.cidrs", "192.168.0.0/24")).scopeGuard();
        assertEquals("192.168.0.107", LabConfig.pickLanIp(candidates, guard::isAuthorised).orElseThrow());
        assertEquals("192.168.56.1", LabConfig.pickLanIp(candidates, ip -> false).orElseThrow(),
                "falls back to the first address when none is in range");
        assertTrue(LabConfig.pickLanIp(List.of(), ip -> true).isEmpty());
    }
}
