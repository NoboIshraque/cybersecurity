package com.mirage.console;

import com.mirage.common.CommandType;
import com.mirage.common.ResponseCommand;
import com.mirage.common.ScopeGuard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Issues manual active-defence commands from the dashboard's override panel.
 * Every target is checked by the same {@link ScopeGuard} the automatic responses
 * use, so an operator can never act on an address outside the lab range. Each
 * attempt (sent or refused) is appended to a log the UI can show.
 */
public final class ResponseController {

    private final ScopeGuard guard;
    private final CommandSink sink;
    private final List<String> log = new ArrayList<>();

    public ResponseController(ScopeGuard guard, CommandSink sink) {
        this.guard = guard;
        this.sink = sink;
    }

    /** Issue one command and return the log line it produced. */
    public synchronized String issue(CommandType type, String targetIp) {
        String line;
        try {
            guard.require(targetIp);
            sink.broadcast(new ResponseCommand(type, targetIp));
            line = Instant.now() + "  " + type + "  →  " + targetIp;
        } catch (SecurityException e) {
            line = Instant.now() + "  REFUSED " + type + "  →  " + targetIp + "  (" + e.getMessage() + ")";
        }
        log.add(line);
        return line;
    }

    public synchronized List<String> log() {
        return List.copyOf(log);
    }
}
