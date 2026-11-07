package com.mirage;

import com.mirage.common.Classification;
import com.mirage.common.LabConfig;
import com.mirage.common.Severity;
import com.mirage.console.MirrorCounterStrategy;
import com.mirage.specter.AttackStrategy;
import com.mirage.specter.Campaign;
import com.mirage.specter.Campaigns;
import com.mirage.specter.PortScanAttack;
import com.mirage.specter.Target;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-21: the recon and mirror scans were hard-coded to 8020-8085. Moving any port (the setup
 * guide suggests it when 8080 is busy) made the scans miss, and on one laptop Specter's own
 * recon covered 8080, its own counter-watch: "SCAN COMPLETE — 4 OPEN" and a false
 * "INCOMING COUNTER-ATTACK" before any counter was launched.
 */
class ScanPortsTest {

    private static PortScanAttack scanIn(Campaign campaign) {
        for (AttackStrategy s : campaign.strategies()) {
            if (s instanceof PortScanAttack scan) {
                return scan;
            }
        }
        throw new AssertionError("no port scan in " + campaign.name());
    }

    @Test
    void reconScansTheConfiguredDecoysAndNotTheLocalCounterWatch() {
        LabConfig defaults = LabConfig.defaults();
        List<Integer> ports = scanIn(Campaigns.recon(defaults)).ports();
        assertEquals(List.of(8021, 8023, 8081), ports);
        assertFalse(ports.contains(defaults.counterWatchPort()), "one laptop: never Specter's own counter-watch");
        assertEquals(ports, scanIn(Campaigns.forChoice("Full campaign", defaults)).ports(),
                "the full campaign scans the same ports");
    }

    @Test
    void movedPortsAreFollowed() {
        LabConfig moved = LabConfig.from(Map.of("decoy.http.port", "9081", "decoy.ftp.port", "9021",
                "decoy.telnet.port", "9023", "counterwatch.port", "9080"));
        assertEquals(List.of(9021, 9023, 9081), scanIn(Campaigns.recon(moved)).ports());
    }

    @Test
    void mirrorScansReachTheConfiguredCounterWatchOnly() {
        MirrorCounterStrategy mirror = new MirrorCounterStrategy(9080);
        Campaign scanBack = mirror.counterFor(new Classification("scan", "T1046", Severity.MEDIUM)).orElseThrow();
        assertEquals(List.of(9080), scanIn(scanBack).ports());
        Campaign probe = mirror.counterFor(new Classification("bait", "T1485", Severity.CRITICAL)).orElseThrow();
        assertEquals(List.of(9080), scanIn(probe).ports());
        assertEquals(List.of(9080), scanIn(mirror.fullCounter()).ports());
    }

    /** The one-laptop layout over real sockets: 3 decoys open, the counter-watch next to them untouched. */
    @Test
    void oneLaptopReconFindsTheThreeDecoysAndLeavesTheCounterWatchAlone() throws Exception {
        List<ServerSocket> decoys = new ArrayList<>();
        try (ServerSocket counterWatch = new ServerSocket(0)) {
            for (int i = 0; i < 3; i++) {
                decoys.add(new ServerSocket(0));
            }
            LabConfig config = LabConfig.from(Map.of(
                    "decoy.http.port", String.valueOf(decoys.get(0).getLocalPort()),
                    "decoy.ftp.port", String.valueOf(decoys.get(1).getLocalPort()),
                    "decoy.telnet.port", String.valueOf(decoys.get(2).getLocalPort()),
                    "counterwatch.port", String.valueOf(counterWatch.getLocalPort())));
            List<String> lines = new ArrayList<>();
            scanIn(Campaigns.recon(config)).run(new Target("127.0.0.1", 0), (level, text) -> lines.add(text));

            assertTrue(lines.contains("SCAN COMPLETE — 3 OPEN"), lines.toString());
            counterWatch.setSoTimeout(200);
            assertThrows(SocketTimeoutException.class, counterWatch::accept,
                    "Specter's recon must not touch its own counter-watch");
        } finally {
            for (ServerSocket s : decoys) {
                closeQuietly(s);
            }
        }
    }

    private static void closeQuietly(ServerSocket s) {
        try {
            s.close();
        } catch (IOException e) {
            // ignore
        }
    }
}
