package com.mirage.console;

import com.mirage.common.Alert;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerProfile;
import com.mirage.common.Classification;
import com.mirage.common.LedgerEntry;
import com.mirage.common.Severity;
import com.mirage.common.trace.TraceLevel;
import com.mirage.common.trace.TraceSink;
import com.mirage.console.detection.DetectionRule;
import com.mirage.console.response.ResponseAction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single consumer in the producer-consumer pipeline. Network threads are
 * producers that enqueue events; this one thread owns all profile and ledger
 * mutation, which keeps shared state thread-safe with almost no locking.
 *
 * <p>Each step can also be narrated to a {@link TraceSink} for the live process
 * shells. Tracing only reads; it never changes what the engine decides.
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
    private volatile TraceSink trace = TraceSink.NONE;
    private volatile AutoResponse autoResponse = new AutoResponse(true);

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

    /** Narrate each pipeline step to the shells (default: none). */
    public void setTrace(TraceSink trace) {
        this.trace = trace == null ? TraceSink.NONE : trace;
    }

    /** Share the automatic-response on/off switch with the dashboard (default: on). */
    public void setAutoResponse(AutoResponse autoResponse) {
        this.autoResponse = autoResponse == null ? new AutoResponse(true) : autoResponse;
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
        trace.line("console", "engine", event.sourceIp(), TraceLevel.INFO,
                "dequeued " + event.decoyType() + " " + event.action() + " from " + event.sourceIp()
                        + "  (profile: " + profile.events().size() + " events, saved to H2)");

        List<String> matched = new ArrayList<>();
        for (DetectionRule rule : rules) {
            rule.classify(profile).ifPresent(c -> {
                matched.add(rule.getClass().getSimpleName());
                raise(profile, c, rule.getClass().getSimpleName());
            });
        }
        if (matched.isEmpty()) {
            trace.line("console", "rule", event.sourceIp(), TraceLevel.INFO,
                    rules.size() + " rules checked, no match");
        }
    }

    private void raise(AttackerProfile profile, Classification classification, String ruleName) {
        String ip = profile.sourceIp();
        trace.line("console", "rule", ip, levelFor(classification.severity()),
                ruleName + " MATCH  " + classification.label() + "  " + classification.attackTechnique()
                        + "  " + classification.severity());

        Alert alert = new Alert(ip, classification, Instant.now());
        LedgerEntry entry = ledger.append(alert);
        trace.line("console", "ledger", ip, TraceLevel.INFO,
                "#" + (ledger.size() - 1) + "  sha256=" + shortHash(entry.hash())
                        + "  prev=" + shortHash(entry.prevHash())
                        + (entry.signature() == null || entry.signature().isEmpty() ? "" : "  signed (ECDSA)"));
        persistence.saveAlert(entry);
        bus.publish(alert);

        if (!autoResponse.isEnabled()) {
            trace.line("console", "response", ip, TraceLevel.WARN,
                    "auto-response OFF — alert recorded, waiting for the operator");
            return;
        }
        try {
            List<ResponseAction> actions = policy.actionsFor(ip, classification);
            for (ResponseAction action : actions) {
                trace.line("console", "response", ip, TraceLevel.WARN,
                        "auto-response: " + action.describe() + "  (ScopeGuard ok)");
                action.execute();
            }
        } catch (SecurityException e) {
            // A non-network source (e.g. a bait file) or an out-of-scope address:
            // the alert is still recorded and surfaced, but no network response runs.
            trace.line("console", "response", ip, TraceLevel.INFO,
                    "no network response: " + e.getMessage());
            System.out.println("[info] no network response for " + ip + ": " + e.getMessage());
        }
    }

    private static TraceLevel levelFor(Severity severity) {
        return switch (severity) {
            case LOW, MEDIUM -> TraceLevel.WARN;
            case HIGH, CRITICAL -> TraceLevel.BAD;
        };
    }

    private static String shortHash(String hash) {
        return hash == null ? "-" : hash.substring(0, Math.min(10, hash.length())) + "…";
    }
}
