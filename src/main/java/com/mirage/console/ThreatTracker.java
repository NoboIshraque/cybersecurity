package com.mirage.console;

import com.mirage.common.Alert;
import com.mirage.common.CommandType;
import com.mirage.common.trace.TraceEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The GUI-free brain behind the threat popups: tracks one {@link ThreatLevel} per
 * source and reports when it changes. Alerts can only raise a level. A Sensor's
 * ACK of a BLOCK on a known source neutralises it (and later alerts leave it
 * neutralised); an ACK of an UNBLOCK restores the level it had before.
 *
 * <p>Alerts arrive on the engine thread and ACK traces on the receive threads, so
 * every method is synchronized.
 */
public final class ThreatTracker {

    private static final String CONFIRMS = "confirms: ";

    private final Map<String, ThreatLevel> levels = new HashMap<>();
    private final Map<String, ThreatLevel> beforeBlock = new HashMap<>();

    /** Feed an alert; returns the new level if this alert changed the source's level. */
    public synchronized Optional<ThreatLevel> onAlert(Alert alert) {
        String ip = alert.sourceIp();
        ThreatLevel candidate = ThreatLevel.forSeverity(alert.classification().severity());
        ThreatLevel current = levels.get(ip);
        if (current == ThreatLevel.NEUTRALISED) {
            // still blocked: remember the worst it has tried, in case it is unblocked
            beforeBlock.merge(ip, candidate, ThreatTracker::worse);
            return Optional.empty();
        }
        if (current == null || candidate.ordinal() > current.ordinal()) {
            levels.put(ip, candidate);
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /** Feed a trace line; only a Sensor ACK of BLOCK/UNBLOCK on a known source changes anything. */
    public synchronized Optional<ThreatLevel> onTrace(TraceEvent event) {
        if (!"console".equals(event.channel()) || !"ack".equals(event.stage()) || event.subject() == null) {
            return Optional.empty();
        }
        String ip = event.subject();
        ThreatLevel current = levels.get(ip);
        if (current == null) {
            return Optional.empty();
        }
        CommandType acked = ackedCommand(event.text());
        if (acked == CommandType.BLOCK && current != ThreatLevel.NEUTRALISED) {
            beforeBlock.put(ip, current);
            levels.put(ip, ThreatLevel.NEUTRALISED);
            return Optional.of(ThreatLevel.NEUTRALISED);
        }
        if (acked == CommandType.UNBLOCK && current == ThreatLevel.NEUTRALISED) {
            ThreatLevel restored = beforeBlock.remove(ip);
            levels.put(ip, restored);
            return Optional.of(restored);
        }
        return Optional.empty();
    }

    /** The source's current level, or {@code null} if it has raised no alert. */
    public synchronized ThreatLevel levelOf(String ip) {
        return levels.get(ip);
    }

    /** Parses "sensor-1 confirms: BLOCK 10.0.0.5 applied" → BLOCK. */
    static CommandType ackedCommand(String text) {
        if (text == null) {
            return null;
        }
        int at = text.indexOf(CONFIRMS);
        if (at < 0) {
            return null;
        }
        String rest = text.substring(at + CONFIRMS.length()).trim();
        int space = rest.indexOf(' ');
        String word = space < 0 ? rest : rest.substring(0, space);
        try {
            return CommandType.valueOf(word);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ThreatLevel worse(ThreatLevel a, ThreatLevel b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
