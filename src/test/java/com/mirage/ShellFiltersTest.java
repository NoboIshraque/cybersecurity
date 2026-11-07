package com.mirage;

import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;
import com.mirage.console.ShellFilters;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Each Console shell shows the right lines and only those. */
class ShellFiltersTest {

    private static final String IP = "10.0.0.5";
    private static final String OTHER = "10.0.0.6";

    private static TraceEvent ev(String channel, String stage, String subject) {
        return new TraceEvent(Instant.now(), channel, "t", stage, subject, TraceLevel.INFO, "x");
    }

    @Test
    void threatPopupShowsThePipelineForItsSourceOnly() {
        var f = ShellFilters.threat(IP);
        assertTrue(f.test(ev("console", "rule", IP)));
        assertTrue(f.test(ev("console", "ack", IP)));
        assertFalse(f.test(ev("console", "rule", OTHER)));
        assertFalse(f.test(ev("console", "rx", null)), "link up/down lines are not about a source");
        assertFalse(f.test(ev("op", "command", IP)), "operator lines go to the operation shells");
    }

    @Test
    void defenceShellShowsCommandDispatchAndAck() {
        var f = ShellFilters.defence(IP);
        assertTrue(f.test(ev("op", "command", IP)));
        assertTrue(f.test(ev("console", "dispatch", IP)));
        assertTrue(f.test(ev("console", "ack", IP)));
        assertFalse(f.test(ev("console", "rule", IP)));
        assertFalse(f.test(ev("op", "counter", IP)));
        assertFalse(f.test(ev("op", "command", OTHER)));
    }

    @Test
    void counterShellShowsCounterAndEveryCounterThread() {
        var f = ShellFilters.counter(IP);
        assertTrue(f.test(ev("op", "counter", IP)));
        assertTrue(f.test(ev("op", "port-scan", IP)), "the Attacker traces each strategy under its own stage");
        assertFalse(f.test(ev("op", "command", IP)));
        assertFalse(f.test(ev("op", "counter", OTHER)));
        assertFalse(f.test(ev("attack", "port-scan", IP)), "Specter's own channel is not the Console's");
    }

    @Test
    void pipelineShellShowsAllConsoleLines() {
        var f = ShellFilters.pipeline();
        assertTrue(f.test(ev("console", "rx", null)));
        assertTrue(f.test(ev("console", "rule", OTHER)));
        assertFalse(f.test(ev("op", "command", IP)));
    }
}
