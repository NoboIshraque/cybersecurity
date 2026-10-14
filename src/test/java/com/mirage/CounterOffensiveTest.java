package com.mirage;

import com.mirage.common.Classification;
import com.mirage.common.ScopeGuard;
import com.mirage.common.Severity;
import com.mirage.console.CounterOffensive;
import com.mirage.console.CounterStrategy;
import com.mirage.specter.AttackStrategy;
import com.mirage.specter.Attacker;
import com.mirage.specter.Campaign;
import com.mirage.specter.Target;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CounterOffensiveTest {

    /** A harmless strategy so the test never touches the network. */
    private static final class NoOpAttack implements AttackStrategy {
        @Override public void run(Target target) { /* no network */ }
        @Override public String name() { return "noop"; }
    }

    @Test
    void mirrorsAtAnInRangeSourceButRefusesOutOfRange() {
        CounterStrategy strategy = technique -> Optional.of(new Campaign("test", List.of(new NoOpAttack())));
        Attacker attacker = new Attacker(new ScopeGuard(List.of("10.10.0.0/24")), line -> { });
        CounterOffensive counter = new CounterOffensive(attacker, strategy, 8080);
        Classification brute = new Classification("Credential brute-force", "T1110", Severity.HIGH);

        String inRange = counter.counter("10.10.0.5", brute);
        assertTrue(inRange.contains("COUNTER"), inRange);
        assertFalse(inRange.contains("REFUSED"));

        String outOfRange = counter.counter("8.8.8.8", brute);
        assertTrue(outOfRange.contains("REFUSED"), outOfRange);

        assertEquals(2, counter.log().size());
        attacker.shutdown();
    }
}
