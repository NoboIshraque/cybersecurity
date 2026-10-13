package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.ScopeGuard;
import com.mirage.sensor.FakeHttpLoginDecoy;
import com.mirage.specter.Attacker;
import com.mirage.specter.Campaign;
import com.mirage.specter.PasswordSprayAttack;
import com.mirage.specter.Target;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SpecterAttackTest {

    @Test
    void sprayCampaignHitsTheDecoyWithAllUsers() throws Exception {
        BlockingQueue<AttackEvent> events = new LinkedBlockingQueue<>();
        FakeHttpLoginDecoy decoy = new FakeHttpLoginDecoy(0);
        decoy.start(events::add);
        int port = decoy.boundPort();

        Attacker attacker = new Attacker(new ScopeGuard(List.of("127.0.0.1/32")));
        attacker.launch(new Campaign("spray", List.of(
                new PasswordSprayAttack(List.of("alice", "bob", "carol"), "Winter2026!"))),
                new Target("127.0.0.1", port));

        Set<String> users = new HashSet<>();
        long deadline = System.currentTimeMillis() + 4000;
        while (users.size() < 3 && System.currentTimeMillis() < deadline) {
            AttackEvent e = events.poll(500, TimeUnit.MILLISECONDS);
            if (e != null && e.action() == Action.LOGIN_ATTEMPT) {
                users.add(e.payload().split(":")[0]);
            }
        }
        assertEquals(Set.of("alice", "bob", "carol"), users);

        attacker.shutdown();
        decoy.stop();
    }

    @Test
    void attackerRefusesOutOfScopeTarget() {
        Attacker attacker = new Attacker(new ScopeGuard(List.of("10.10.0.0/24")));
        assertThrows(SecurityException.class, () -> attacker.launch(
                new Campaign("spray", List.of(new PasswordSprayAttack(List.of("a"), "p"))),
                new Target("8.8.8.8", 80)));
    }
}
