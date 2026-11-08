package com.mirage;

import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;
import com.mirage.ui.shell.ShellModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShellModelTest {

    private static TraceEvent line(String thread, TraceLevel level, String text) {
        return new TraceEvent(Instant.now(), "console", thread, "stage", "10.10.0.5", level, text);
    }

    @Test
    void pollDeliversInOrderAndRespectsTheBatchSize() {
        ShellModel model = new ShellModel();
        for (int i = 0; i < 5; i++) {
            model.add(line("engine", TraceLevel.INFO, "line " + i));
        }
        List<ShellModel.Line> first = model.poll(2);
        assertEquals(2, first.size());
        assertEquals("line 0", first.get(0).text());
        assertEquals("line 1", first.get(1).text());
        assertTrue(model.hasPending());

        List<ShellModel.Line> rest = model.poll(10);
        assertEquals(3, rest.size());
        assertEquals("line 4", rest.get(2).text());
        assertFalse(model.hasPending());
    }

    @Test
    void eachThreadGetsAStableDistinctColour() {
        ShellModel model = new ShellModel();
        model.add(line("rx-sensor-1", TraceLevel.INFO, "a"));
        model.add(line("engine", TraceLevel.INFO, "b"));
        model.add(line("rx-sensor-1", TraceLevel.INFO, "c"));

        int rx = model.colorIndex("rx-sensor-1");
        int engine = model.colorIndex("engine");
        assertNotEquals(rx, engine);
        assertEquals(rx, model.colorIndex("rx-sensor-1")); // stable on repeat
    }

    @Test
    void colourIndicesCycleThroughThePalette() {
        ShellModel model = new ShellModel();
        for (int i = 0; i < ShellModel.PALETTE_SIZE + 1; i++) {
            model.add(line("t" + i, TraceLevel.INFO, "x"));
        }
        // The 7th distinct thread wraps back onto the first palette slot.
        assertEquals(model.colorIndex("t0"), model.colorIndex("t" + ShellModel.PALETTE_SIZE));
    }

    @Test
    void lanesAppearInFirstSeenOrder() {
        ShellModel model = new ShellModel();
        model.add(line("scan", TraceLevel.INFO, "x"));
        model.add(line("brute", TraceLevel.INFO, "y"));
        List<ShellModel.Lane> lanes = model.lanes();
        assertEquals(2, lanes.size());
        assertEquals("scan", lanes.get(0).thread());
        assertEquals("brute", lanes.get(1).thread());
    }

    @Test
    void finishMarkerMovesAThreadToDone() {
        ShellModel model = new ShellModel();
        model.add(line("scan", TraceLevel.INFO, "start port-scan"));
        assertEquals(ShellModel.State.RUNNING, model.stateOf("scan"));
        model.add(line("scan", TraceLevel.INFO, "port-scan finished"));
        assertEquals(ShellModel.State.DONE, model.stateOf("scan"));
    }

    @Test
    void concurrentThreadsAreReportedAsRunningForSplitView() {
        ShellModel model = new ShellModel();
        model.add(line("mirror-scan", TraceLevel.INFO, "start"));
        model.add(line("mirror-brute", TraceLevel.INFO, "start"));
        model.add(line("mirror-spray", TraceLevel.INFO, "start"));
        assertEquals(3, model.runningThreads().size());
    }

    @Test
    void bannerLevelIsFlagged() {
        ShellModel model = new ShellModel();
        model.add(line("attack", TraceLevel.BANNER, "ACCESS GRANTED"));
        assertTrue(model.poll(1).get(0).banner());
    }
}
