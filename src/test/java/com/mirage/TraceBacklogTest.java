package com.mirage;

import com.mirage.common.trace.TraceBacklog;
import com.mirage.common.trace.TraceBus;
import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** A shell that opens late sees what it missed, then live lines, with no gap or duplicate. */
class TraceBacklogTest {

    private static void say(TraceBus bus, String text) {
        bus.line("console", "rule", "10.0.0.5", TraceLevel.INFO, text);
    }

    private static List<String> texts(List<TraceEvent> events) {
        return events.stream().map(TraceEvent::text).toList();
    }

    @Test
    void replaysThenGoesLive() {
        TraceBus bus = new TraceBus();
        TraceBacklog backlog = new TraceBacklog(100);
        bus.subscribe(backlog);
        say(bus, "a");
        say(bus, "b");

        List<TraceEvent> late = new ArrayList<>();
        List<TraceEvent> liveOnly = new ArrayList<>();
        backlog.attach(late::add, true);
        backlog.attach(liveOnly::add, false);
        say(bus, "c");

        assertEquals(List.of("a", "b", "c"), texts(late));
        assertEquals(List.of("c"), texts(liveOnly));
    }

    @Test
    void forgetsTheOldestBeyondCapacityAndDetaches() {
        TraceBus bus = new TraceBus();
        TraceBacklog backlog = new TraceBacklog(2);
        bus.subscribe(backlog);
        say(bus, "1");
        say(bus, "2");
        say(bus, "3");
        assertEquals(List.of("2", "3"), texts(backlog.history()));

        List<TraceEvent> got = new ArrayList<>();
        Consumer<TraceEvent> listener = got::add;
        backlog.attach(listener, false);
        backlog.detach(listener);
        say(bus, "4");
        assertTrue(got.isEmpty());
        assertEquals(0, backlog.listenerCount());
    }

    @Test
    void aBrokenListenerDoesNotStopOthers() {
        TraceBacklog backlog = new TraceBacklog(10);
        List<TraceEvent> got = new ArrayList<>();
        backlog.attach(e -> { throw new IllegalStateException("broken display"); }, false);
        backlog.attach(got::add, false);
        TraceBus bus = new TraceBus();
        bus.subscribe(backlog);
        assertDoesNotThrow(() -> say(bus, "x"));
        assertEquals(List.of("x"), texts(got));
    }
}
