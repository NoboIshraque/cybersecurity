package com.mirage;

import com.mirage.common.Classification;
import com.mirage.common.ScopeGuard;
import com.mirage.common.Severity;
import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;
import com.mirage.common.trace.TraceSink;
import com.mirage.console.CounterOffensive;
import com.mirage.console.CounterStrategy;
import com.mirage.specter.AttackStrategy;
import com.mirage.specter.AttackTrace;
import com.mirage.specter.Attacker;
import com.mirage.specter.Campaign;
import com.mirage.specter.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-20: a strategy that throws must not vanish into the pool. Its shell gets an ERROR line
 * and banner, the rest of the campaign still runs, and an impossible port is refused before
 * anything is launched.
 */
class StrategyFailureTest {

    private static final ScopeGuard LOOPBACK = new ScopeGuard(List.of("127.0.0.1/32"));

    /** A strategy with a bug in it. */
    private static final class Crashing implements AttackStrategy {
        @Override
        public void run(Target target, AttackTrace trace) {
            trace.line(TraceLevel.COMMAND, "$ crash");
            throw new IllegalStateException("boom in strategy");
        }

        @Override
        public String name() {
            return "crashing";
        }
    }

    /** A well-behaved strategy that records that it ran. */
    private static final class Marker implements AttackStrategy {
        final CountDownLatch ran = new CountDownLatch(1);

        @Override
        public void run(Target target, AttackTrace trace) {
            trace.line(TraceLevel.GOOD, "marker ran");
            ran.countDown();
        }

        @Override
        public String name() {
            return "marker";
        }
    }

    /** Collects trace events; {@code done} opens on the line that ends the run. */
    private static final class Recorder implements TraceSink {
        final List<TraceEvent> events = new CopyOnWriteArrayList<>();
        final CountDownLatch done = new CountDownLatch(1);
        private final String endMarker;

        Recorder(String endMarker) {
            this.endMarker = endMarker;
        }

        @Override
        public void emit(TraceEvent event) {
            events.add(event);
            if (event.text().contains(endMarker)) {
                done.countDown();
            }
        }

        boolean has(String stage, TraceLevel level, String fragment) {
            return events.stream().anyMatch(e -> e.stage().equals(stage) && e.level() == level
                    && e.text().contains(fragment));
        }
    }

    @Test
    void crashInCampaignShowsErrorBannerAndCampaignCarriesOn() throws Exception {
        Recorder rec = new Recorder("complete");
        List<String> log = new CopyOnWriteArrayList<>();
        Attacker attacker = new Attacker(LOOPBACK, log::add);
        attacker.setTrace(rec, "attack");
        Marker marker = new Marker();
        try {
            attacker.launch(new Campaign("demo", List.of(new Crashing(), marker)),
                    new Target("127.0.0.1", 8081));
            assertTrue(rec.done.await(5, TimeUnit.SECONDS), "campaign never completed: " + rec.events);
        } finally {
            attacker.shutdown();
        }
        assertTrue(rec.has("crashing", TraceLevel.BAD, "boom in strategy"), rec.events.toString());
        assertTrue(rec.has("crashing", TraceLevel.BANNER, "ERROR"), rec.events.toString());
        assertFalse(rec.has("crashing", TraceLevel.INFO, "crashing finished"), rec.events.toString());
        assertTrue(rec.has("marker", TraceLevel.GOOD, "marker ran"), "next strategy should still run");
        assertTrue(log.stream().anyMatch(l -> l.contains("crashing") && l.contains("boom in strategy")), log.toString());
    }

    @Test
    void crashInConcurrentLaunchShowsErrorBanner() throws Exception {
        Recorder rec = new Recorder("ERROR");
        Attacker attacker = new Attacker(LOOPBACK);
        attacker.setTrace(rec, "attack");
        Marker marker = new Marker();
        try {
            attacker.launchConcurrent(new Campaign("all", List.of(new Crashing(), marker)),
                    new Target("127.0.0.1", 8081));
            assertTrue(rec.done.await(5, TimeUnit.SECONDS), "no ERROR banner: " + rec.events);
            assertTrue(marker.ran.await(5, TimeUnit.SECONDS));
        } finally {
            attacker.shutdown();
        }
        assertTrue(rec.has("crashing", TraceLevel.BANNER, "ERROR"), rec.events.toString());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 65536, 70000})
    void impossiblePortIsRefusedUpFront(int port) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new Target("127.0.0.1", port));
        assertTrue(e.getMessage().contains(String.valueOf(port)), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 8081, 65535})
    void validPortIsAccepted(int port) {
        assertEquals(port, new Target("127.0.0.1", port).port());
    }

    @Test
    void counterWithImpossiblePortReportsInsteadOfThrowing() {
        Campaign campaign = new Campaign("mirror", List.of(new Marker()));
        CounterStrategy strategy = new CounterStrategy() {
            @Override
            public Optional<Campaign> counterFor(Classification technique) {
                return Optional.of(campaign);
            }

            @Override
            public Campaign fullCounter() {
                return campaign;
            }
        };
        Attacker attacker = new Attacker(LOOPBACK);
        Recorder rec = new Recorder("never");
        CounterOffensive counter = new CounterOffensive(attacker, strategy, 70000);
        counter.setTrace(rec);
        try {
            String one = assertDoesNotThrow(() -> counter.counter("127.0.0.1",
                    new Classification("Credential brute-force", "T1110", Severity.HIGH)));
            String all = assertDoesNotThrow(() -> counter.counterAll("127.0.0.1"));
            assertTrue(one.contains("70000"), one);
            assertTrue(all.contains("70000"), all);
            assertTrue(rec.has("counter", TraceLevel.BAD, "70000"), rec.events.toString());
        } finally {
            attacker.shutdown();
        }
    }
}
