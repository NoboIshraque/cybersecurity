package com.mirage;

import com.mirage.common.trace.TraceSink;
import com.mirage.sensor.ConnectionGate;
import com.mirage.sensor.FakeHttpLoginDecoy;
import com.mirage.specter.AttackTrace;
import com.mirage.specter.CounterWatch;
import com.mirage.specter.HoneytokenReplayAttack;
import com.mirage.specter.Target;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A blocked decoy and a silent listener must not look the same: a block resets the
 * connection (CONNECTION BLOCKED), the accept-and-log counter-watch closes quietly
 * without answering (NO REPLY).
 */
class ProbeOutcomeTest {

    private static List<String> replay(int port) {
        List<String> lines = new ArrayList<>();
        AttackTrace trace = (level, text) -> lines.add(level + " " + text);
        new HoneytokenReplayAttack("svc_backup:S3cr3tBackup!").run(new Target("127.0.0.1", port), trace);
        return lines;
    }

    @Test
    void blockedDecoyReadsAsBlocked() throws Exception {
        FakeHttpLoginDecoy decoy = new FakeHttpLoginDecoy(0);
        decoy.start(e -> { }, ip -> ConnectionGate.Decision.BLOCK);
        try {
            List<String> lines = replay(decoy.boundPort());
            assertTrue(lines.contains("BANNER CONNECTION BLOCKED"), lines.toString());
        } finally {
            decoy.stop();
        }
    }

    @Test
    void silentCounterWatchReadsAsNoReplyNotBlocked() throws Exception {
        CounterWatch watch = new CounterWatch(0, TraceSink.NONE);
        int port = watch.start();
        try {
            List<String> lines = replay(port);
            assertTrue(lines.contains("BANNER NO REPLY"), lines.toString());
            assertTrue(lines.stream().noneMatch(l -> l.contains("BLOCKED")), lines.toString());
        } finally {
            watch.stop();
        }
    }
}
