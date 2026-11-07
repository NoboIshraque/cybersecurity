package com.mirage;

import com.mirage.common.trace.TraceBus;
import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TraceBusTest {

    @Test
    void deliversToEverySubscriberInOrder() {
        TraceBus bus = new TraceBus();
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        bus.subscribe(e -> a.add(e.text()));
        bus.subscribe(e -> b.add(e.text()));

        bus.line("console", "rx", "10.10.0.5", TraceLevel.INFO, "one");
        bus.line("console", "engine", "10.10.0.5", TraceLevel.INFO, "two");

        assertEquals(List.of("one", "two"), a);
        assertEquals(List.of("one", "two"), b);
    }

    @Test
    void stampsThreadAndTime() {
        TraceBus bus = new TraceBus();
        List<TraceEvent> seen = new ArrayList<>();
        bus.subscribe(seen::add);
        bus.line("attack", "port-scan", "10.10.0.5", TraceLevel.GOOD, "open");
        assertEquals(1, seen.size());
        assertEquals(Thread.currentThread().getName(), seen.get(0).thread());
        assertNotNull(seen.get(0).time());
        assertEquals("attack", seen.get(0).channel());
    }

    @Test
    void aBrokenListenerDoesNotStopTheOthers() {
        TraceBus bus = new TraceBus();
        List<String> good = new ArrayList<>();
        bus.subscribe(e -> { throw new RuntimeException("boom"); });
        bus.subscribe(e -> good.add(e.text()));
        assertDoesNotThrow(() -> bus.line("console", "rx", null, TraceLevel.INFO, "still works"));
        assertEquals(List.of("still works"), good);
    }
}
