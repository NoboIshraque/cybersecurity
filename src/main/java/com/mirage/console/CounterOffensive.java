package com.mirage.console;

import com.mirage.common.Classification;
import com.mirage.common.ScopeGuard;
import com.mirage.specter.Attacker;
import com.mirage.specter.Campaign;
import com.mirage.specter.Target;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The lab-bounded counter-offensive. On the operator's command it reads the
 * attacker's detected technique, picks the matching Specter campaign (via a
 * {@link CounterStrategy}), and turns that same method back on the source.
 *
 * <p>The campaign runs through the Specter {@link Attacker}, which calls
 * {@link ScopeGuard#require(String)} before anything leaves the machine. A source
 * outside the configured lab range is therefore refused, so this can never reach a
 * real third party: it is a controlled, in-range demonstration of "turning it
 * around", not a hack-back. It fires only when an operator triggers it, never
 * automatically.
 */
public final class CounterOffensive {

    private final Attacker attacker; // ScopeGuard-gated at launch
    private final CounterStrategy strategy;
    private final int targetPort;
    private final List<String> log = new ArrayList<>();

    public CounterOffensive(Attacker attacker, CounterStrategy strategy, int targetPort) {
        this.attacker = attacker;
        this.strategy = strategy;
        this.targetPort = targetPort;
    }

    /** Turn the source's own technique back on it; returns (and logs) the result line. */
    public synchronized String counter(String sourceIp, Classification technique) {
        Optional<Campaign> campaign = strategy.counterFor(technique);
        String line;
        if (campaign.isEmpty()) {
            line = Instant.now() + "  no counter mapped for " + technique.label();
        } else {
            try {
                attacker.launch(campaign.get(), new Target(sourceIp, targetPort));
                line = Instant.now() + "  COUNTER '" + campaign.get().name() + "' ("
                        + technique.attackTechnique() + ")  →  " + sourceIp;
            } catch (SecurityException e) {
                line = Instant.now() + "  REFUSED counter  →  " + sourceIp + "  (" + e.getMessage() + ")";
            }
        }
        log.add(line);
        return line;
    }

    /**
     * Turn several methods on the source at once: every counter technique runs
     * concurrently, each on its own thread, through the same ScopeGuard check.
     */
    public synchronized String counterAll(String sourceIp) {
        Campaign campaign = strategy.fullCounter();
        String line;
        try {
            attacker.launchConcurrent(campaign, new Target(sourceIp, targetPort));
            line = Instant.now() + "  COUNTER-ALL (" + campaign.strategies().size()
                    + " methods, concurrent)  →  " + sourceIp;
        } catch (SecurityException e) {
            line = Instant.now() + "  REFUSED counter  →  " + sourceIp + "  (" + e.getMessage() + ")";
        }
        log.add(line);
        return line;
    }

    public synchronized List<String> log() {
        return List.copyOf(log);
    }
}

