package com.mirage;

import com.mirage.sensor.ConnectionGate;
import com.mirage.sensor.FakeHttpLoginDecoy;
import com.mirage.specter.AttackStrategy;
import com.mirage.specter.AttackTrace;
import com.mirage.specter.BruteForceAttack;
import com.mirage.specter.HoneytokenReplayAttack;
import com.mirage.specter.PasswordSprayAttack;
import com.mirage.specter.Target;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The attack strategies report an honest outcome by actually reading the decoy's
 * reply: a normal reject, a dropped connection (block), or a stalled one (tarpit).
 */
class StrategyOutcomeTest {

    private final List<String> lines = new ArrayList<>();
    private final AttackTrace trace = (level, text) -> lines.add(level + " " + text);

    @Test
    void normalDecoyRejectsTheCredentials() throws Exception {
        FakeHttpLoginDecoy decoy = new FakeHttpLoginDecoy(0);
        decoy.start(e -> { }, ConnectionGate.ALLOW_ALL);
        new BruteForceAttack("admin", List.of("wrong1", "wrong2"))
                .run(new Target("127.0.0.1", decoy.boundPort()), trace);
        decoy.stop();

        assertTrue(lines.stream().anyMatch(l -> l.contains("401") || l.toLowerCase().contains("reject")),
                lines.toString());
        assertTrue(lines.stream().noneMatch(l -> l.contains("blocked") || l.contains("tarpit")), lines.toString());
    }

    @Test
    void blockedSourceSeesTheConnectionDropped() throws Exception {
        FakeHttpLoginDecoy decoy = new FakeHttpLoginDecoy(0);
        ConnectionGate blockAll = sourceIp -> ConnectionGate.Decision.BLOCK;
        decoy.start(e -> { }, blockAll);
        new BruteForceAttack("admin", List.of("x"))
                .run(new Target("127.0.0.1", decoy.boundPort()), trace);
        decoy.stop();

        assertTrue(lines.stream().anyMatch(l -> l.contains("blocked")), lines.toString());
        assertTrue(lines.contains("BANNER CONNECTION BLOCKED"), lines.toString());
    }

    /**
     * B-24: the outcome above depended on whether the decoy's reset beat the request. Every
     * credential strategy must call it a block every time, so run each one many times.
     */
    @Test
    void everyCredentialStrategySeesABlockEveryTime() throws Exception {
        FakeHttpLoginDecoy decoy = new FakeHttpLoginDecoy(0);
        decoy.start(e -> { }, sourceIp -> ConnectionGate.Decision.BLOCK);
        try {
            Target target = new Target("127.0.0.1", decoy.boundPort());
            List<AttackStrategy> strategies = List.of(
                    new BruteForceAttack("admin", List.of("x")),
                    new PasswordSprayAttack(List.of("alice", "bob"), "Winter2026!"),
                    new HoneytokenReplayAttack("svc_backup:S3cr3tBackup!"));
            for (int run = 0; run < 25; run++) {
                for (AttackStrategy strategy : strategies) {
                    lines.clear();
                    strategy.run(target, trace);
                    assertTrue(lines.stream().noneMatch(l -> l.contains("connection failed")),
                            strategy.name() + " run " + run + ": " + lines);
                    assertTrue(lines.contains("BANNER CONNECTION BLOCKED"),
                            strategy.name() + " run " + run + ": " + lines);
                }
            }
        } finally {
            decoy.stop();
        }
    }

    @Test
    void tarpittedSourceSeesNoReply() throws Exception {
        FakeHttpLoginDecoy decoy = new FakeHttpLoginDecoy(0);
        ConnectionGate tarpitAll = sourceIp -> ConnectionGate.Decision.TARPIT;
        decoy.start(e -> { }, tarpitAll);
        new BruteForceAttack("admin", List.of("x"))
                .run(new Target("127.0.0.1", decoy.boundPort()), trace);
        decoy.stop();

        assertTrue(lines.stream().anyMatch(l -> l.contains("tarpit")), lines.toString());
    }
}
