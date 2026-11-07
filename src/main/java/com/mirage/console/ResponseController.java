package com.mirage.console;

import com.mirage.common.CommandType;
import com.mirage.common.ResponseCommand;
import com.mirage.common.ScopeGuard;
import com.mirage.common.trace.TraceLevel;
import com.mirage.common.trace.TraceSink;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Issues manual active-defence commands from the dashboard's override panel.
 * Every target is checked by the same {@link ScopeGuard} the automatic responses
 * use, so an operator can never act on an address outside the lab range. Each
 * attempt (sent or refused) is appended to a log the UI can show, and narrated to
 * a {@link TraceSink} for the operation shell.
 */
public final class ResponseController {

    private final ScopeGuard guard;
    private final CommandSink sink;
    private final List<String> log = new ArrayList<>();
    private volatile TraceSink trace = TraceSink.NONE;

    public ResponseController(ScopeGuard guard, CommandSink sink) {
        this.guard = guard;
        this.sink = sink;
    }

    /** Narrate each operator command to the operation shell (default: none). */
    public void setTrace(TraceSink trace) {
        this.trace = trace == null ? TraceSink.NONE : trace;
    }

    /** Issue one command and return the log line it produced. */
    public synchronized String issue(CommandType type, String targetIp) {
        String line;
        trace.line("op", "command", targetIp, TraceLevel.COMMAND,
                "operator: " + type + " " + targetIp);
        try {
            trace.line("op", "command", targetIp, TraceLevel.INFO,
                    "ScopeGuard.require(" + targetIp + ") …");
            guard.require(targetIp);
            trace.line("op", "command", targetIp, TraceLevel.GOOD, "in range — dispatching");
            sink.broadcast(new ResponseCommand(type, targetIp));
            line = Instant.now() + "  " + type + "  →  " + targetIp;
        } catch (SecurityException e) {
            trace.line("op", "command", targetIp, TraceLevel.BAD,
                    "REFUSED: " + e.getMessage());
            line = Instant.now() + "  REFUSED " + type + "  →  " + targetIp + "  (" + e.getMessage() + ")";
        }
        log.add(line);
        return line;
    }

    public synchronized List<String> log() {
        return List.copyOf(log);
    }
}
