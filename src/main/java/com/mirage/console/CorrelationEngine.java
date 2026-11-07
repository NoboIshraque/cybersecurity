package com.mirage.console;

import com.mirage.common.Alert;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerProfile;
import com.mirage.common.Classification;
import com.mirage.common.LedgerEntry;
import com.mirage.console.detection.DetectionRule;
import com.mirage.console.response.ResponseAction;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single consumer in the producer-consumer pipeline. Network threads are
 * producers that enqueue events; this one thread owns all profile and ledger
 * mutation, which keeps shared state thread-safe with almost no locking.
 */
public final class CorrelationEngine implements Runnable {

    private final BlockingQueue<AttackEvent> events;
    private final List<DetectionRule> rules;
    private final AlertLedger ledger;
    private final AlertBus bus;
    private final ResponsePolicy policy;
    private final Persistence persistence;
    private final AttackerDirectory directory;
    private final Map<String, AttackerProfile> profiles = new ConcurrentHashMap<>();

    public CorrelationEngine(BlockingQueue<AttackEvent> events, List<DetectionRule> rules,
                             AlertLedger ledger, AlertBus bus, ResponsePolicy policy,
                             Persistence persistence) {
        this(events, rules, ledger, bus, policy, persistence, new AttackerDirectory());
    }

    public CorrelationEngine(BlockingQueue<AttackEvent> events, List<DetectionRule> rules,
                             AlertLedger ledger, AlertBus bus, ResponsePolicy policy,
                             Persistence persistence, AttackerDirectory directory) {
        this.events = events;
        this.rules = rules;
        this.ledger = ledger;
        this.bus = bus;
        this.policy = policy;
        this.persistence = persistence;
        this.directory = directory;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                process(events.take());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Package-visible so tests can drive a single event without starting a thread. */
    void process(AttackEvent event) {
        persistence.saveEvent(event);
        AttackerProfile profile = profiles.computeIfAbsent(event.sourceIp(), AttackerProfile::new);
        profile.add(event);
        directory.update(profile); // publish an immutable snapshot for the dashboard
        for (DetectionRule rule : rules) {
            rule.classify(profile).ifPresent(c -> raise(profile, c));
        }
    }

    private void raise(AttackerProfile profile, Classification classification) {
        Alert alert = new Alert(profile.sourceIp(), classification, Instant.now());
        LedgerEntry entry = ledger.append(alert);
        persistence.saveAlert(entry);
        bus.publish(alert);
        try {
            for (ResponseAction action : policy.actionsFor(profile.sourceIp(), classification)) {
                action.execute();
            }
        } catch (SecurityException e) {
            // A non-network source (e.g. a bait file) or an out-of-scope address:
            // the alert is still recorded and surfaced, but no network response runs.
            System.out.println("[info] no network response for " + profile.sourceIp()
                    + ": " + e.getMessage());
        }
    }
}
