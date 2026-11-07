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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The single consumer in the producer-consumer pipeline. Network threads are
 * producers that enqueue events; this one thread owns all profile and ledger
 * mutation, which keeps shared state thread-safe with almost no locking.
 *
 * <p>Rules classify a source's profile, so a rule keeps matching on every
 * later event. The engine raises each rule once per source and again only when
 * that rule's severity for the source rises, so the ledger records each finding
 * instead of one copy per event. {@link #resetAlerts(String)} gives a source a clean
 * slate (the operator unblocked it): from then on the rules see only its new activity.
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
    /** Per source: everything it ever did (the dossier and the database). */
    private final Map<String, AttackerProfile> profiles = new ConcurrentHashMap<>();
    /**
     * Per source: what it did since the operator last unblocked it; the rules judge only this
     * (B-03). Starts equal to the lifetime profile, replaced by an empty one on unblock
     * (engine thread only).
     */
    private final Map<String, AttackerProfile> sinceUnblock = new HashMap<>();
    /** Per source: each rule's highest severity alerted so far (engine thread only). */
    private final Map<String, Map<String, Severity>> alerted = new HashMap<>();
    private final Queue<String> resets = new ConcurrentLinkedQueue<>();
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
                processSafely(events.take());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * One bad event must not end the only consumer thread (B-01): if handling it throws,
     * report it to stderr and the console shell, drop that event and keep consuming.
     */
    private void processSafely(AttackEvent event) {
        try {
            process(event);
        } catch (RuntimeException e) {
            String ip = event == null ? null : event.sourceIp();
            System.err.println("[engine] dropped event " + event + ": " + e);
            trace.line("console", "engine", ip, TraceLevel.BAD,
                    "event dropped, engine keeps running: " + e.getClass().getSimpleName()
                            + (e.getMessage() == null ? "" : " — " + e.getMessage()));
        }
    }

    /** Package-visible so tests can drive a single event without starting a thread. */
    void process(AttackEvent event) {
        applyResets(); // before this event, so it is the first of the new slate
        boolean stored = store("event", () -> persistence.saveEvent(event), event.sourceIp());
        AttackerProfile profile = profiles.computeIfAbsent(event.sourceIp(), AttackerProfile::new);
        profile.add(event);
        directory.update(profile); // publish an immutable snapshot for the dashboard
        AttackerProfile judged = sinceUnblock.computeIfAbsent(event.sourceIp(), ip -> profile);
        if (judged != profile) {
            judged.add(event); // unblocked earlier: the rules see only what came after
        }
        trace.line("console", "engine", event.sourceIp(), TraceLevel.INFO,
                "dequeued " + event.decoyType() + " " + event.action() + " from " + event.sourceIp()
                        + "  (profile: " + profile.eventCount() + " events"
                        + (judged != profile ? ", " + judged.eventCount() + " since unblock" : "")
                        + (stored ? ", saved to H2)" : ", NOT saved to H2)"));

        Map<String, Severity> raised = alerted.computeIfAbsent(event.sourceIp(), ip -> new HashMap<>());
        List<String> matched = new ArrayList<>();
        int stillMatching = 0;
        for (DetectionRule rule : rules) {
            Optional<Classification> hit = rule.classify(judged);
            if (hit.isEmpty()) {
                continue;
            }
            String ruleName = rule.getClass().getSimpleName();
            Severity before = raised.get(ruleName);
            Severity now = hit.get().severity();
            if (before != null && now.ordinal() <= before.ordinal()) {
                stillMatching++; // already alerted for this source at this severity: no duplicate
                continue;
            }
            raised.put(ruleName, now);
            matched.add(ruleName);
            raise(profile, hit.get(), ruleName);
        }
        if (matched.isEmpty()) {
            trace.line("console", "rule", event.sourceIp(), TraceLevel.INFO, stillMatching == 0
                    ? rules.size() + " rules checked, no match"
                    : rules.size() + " rules checked: " + stillMatching
                            + " still match, already alerted for this source — no new alert");
        }
    }

    /**
     * Give a source a clean slate (the operator unblocked it). From its next event on, the rules
     * judge only what it does after the unblock, and alert (and, with auto-response on, answer)
     * afresh. Its earlier findings stay in the ledger once and its dossier keeps the whole
     * history; they are just not raised again by its next packet (B-03).
     * Safe from any thread: the request is queued and applied by the engine thread,
     * which stays the only one touching the alert memory and the profiles.
     */
    public void resetAlerts(String sourceIp) {
        if (sourceIp != null) {
            resets.add(sourceIp);
        }
    }

    private void applyResets() {
        String ip;
        while ((ip = resets.poll()) != null) {
            boolean known = alerted.remove(ip) != null;
            known |= sinceUnblock.put(ip, new AttackerProfile(ip)) != null;
            if (known) {
                trace.line("console", "engine", ip, TraceLevel.INFO, "clean slate for " + ip
                        + " (unblocked): earlier findings stay in the ledger; only new activity is judged");
            }
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
        store("alert", () -> persistence.saveAlert(entry), ip);
        store("profile", () -> persistence.profileAlerted(profile, alert), ip); // attacker_profile (B-30)
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

    /**
     * Run one database write. A storage failure (H2 "value too long", disk full, a locked
     * file) is reported but does not stop detection: the in-memory profile and the ledger
     * still have the record. Returns whether the write succeeded.
     */
    private boolean store(String what, Runnable write, String ip) {
        try {
            write.run();
            return true;
        } catch (RuntimeException e) {
            System.err.println("[engine] could not save " + what + " for " + ip + ": " + e);
            trace.line("console", "engine", ip, TraceLevel.WARN,
                    what + " not saved to H2 (" + e.getClass().getSimpleName()
                            + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ") — detection continues");
            return false;
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
