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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CounterOffensiveTest {

    /** A harmless strategy so the test never touches the network. */
    private static final class NoOpAttack implements AttackStrategy {
        @Override public void run(Target target) { /* no network */ }
        @Override public String name() { return "noop"; }
    }

    @Test
    void mirrorsAtAnInRangeSourceButRefusesOutOfRange() {
        CounterStrategy strategy = new CounterStrategy() {
            @Override public Optional<Campaign> counterFor(Classification t) { return Optional.of(new Campaign("test", List.of(new NoOpAttack()))); }
            @Override public Campaign fullCounter() { return new Campaign("test-full", List.of(new NoOpAttack())); }
        };
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

    @Test
    void counterAllRunsEveryMethodAtTheSameTime() throws Exception {
        int methods = 4;
        // A barrier that only trips if all methods are running simultaneously; if they
        // ran one after another, the first would time out and the latch would never reach 0.
        CyclicBarrier barrier = new CyclicBarrier(methods);
        CountDownLatch finished = new CountDownLatch(methods);
        AttackStrategy blocking = new AttackStrategy() {
            @Override public void run(Target target) {
                try {
                    barrier.await(2, TimeUnit.SECONDS);
                    finished.countDown();
                } catch (Exception ignored) {
                    // broken barrier -> the methods were not concurrent
                }
            }
            @Override public String name() { return "blocking"; }
        };
        List<AttackStrategy> four = List.of(blocking, blocking, blocking, blocking);
        CounterStrategy strategy = new CounterStrategy() {
            @Override public Optional<Campaign> counterFor(Classification t) { return Optional.of(new Campaign("one", four)); }
            @Override public Campaign fullCounter() { return new Campaign("full", four); }
        };
        Attacker attacker = new Attacker(new ScopeGuard(List.of("10.10.0.0/24")), line -> { });
        CounterOffensive counter = new CounterOffensive(attacker, strategy, 8080);

        String inRange = counter.counterAll("10.10.0.5");
        assertTrue(inRange.contains("COUNTER-ALL"), inRange);
        assertTrue(finished.await(3, TimeUnit.SECONDS), "every method must run at the same time");

        assertTrue(counter.counterAll("8.8.8.8").contains("REFUSED"), "out-of-range target must be refused");
        attacker.shutdown();
    }
}
