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
    /**
     * Per source: the latest BLOCK or UNBLOCK broadcast (B-33). An ACK for the other one is late
     * (a second Sensor confirming a block that was already lifted) and is ignored. Tarpit and
     * fake-data commands don't count, so "block, then tarpit" still neutralises.
     */
    private final Map<String, CommandType> latestBlockCommand = new HashMap<>();
    private static final String BROADCAST = "broadcast ";
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
        if (!"console".equals(event.channel()) || event.subject() == null) {
            return Optional.empty();
        }
        if ("dispatch".equals(event.stage())) {
            CommandType sent = broadcastCommand(event.text());
            if (sent == CommandType.BLOCK || sent == CommandType.UNBLOCK) {
                latestBlockCommand.put(event.subject(), sent);
            }
            return Optional.empty();
        }
        if (!"ack".equals(event.stage())) {
            return Optional.empty();
        }
        String ip = event.subject();
        ThreatLevel current = levels.get(ip);
        if (current == null) {
            return Optional.empty();
        }
        CommandType acked = ackedCommand(event.text());
        CommandType latest = latestBlockCommand.get(ip);
        if (latest != null && (acked == CommandType.BLOCK || acked == CommandType.UNBLOCK) && acked != latest) {
            return Optional.empty(); // B-33: confirms a command that was superseded
        }
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
    /** Parses "broadcast BLOCK 10.0.0.5 to 2 sensor(s)" → BLOCK. */
    static CommandType broadcastCommand(String text) {
        if (text == null || !text.startsWith(BROADCAST)) {
            return null;
        }
        String rest = text.substring(BROADCAST.length());
        int space = rest.indexOf(' ');
        try {
            return CommandType.valueOf(space < 0 ? rest : rest.substring(0, space));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

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
