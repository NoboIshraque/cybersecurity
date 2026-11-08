package com.mirage.ui.shell;

import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;
import org.junit.jupiter.api.Test;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FB-03: split view put the thread panes in one row inside a horizontal scroll pane, so of 4–5
 * panes only 1 or 2 were on screen. FB-07: thread panes were only fed new lines, so ticking
 * "Split threads" after lines arrived showed nothing, and a shell switched to split by the
 * simultaneous counter lost the lines of the single counter before it.
 */
class SplitViewTest {

    private static final String[] COUNTER_THREADS =
            {"op-counter-all", "op-port-scan", "op-brute-force", "op-password-spray", "op-honeytoken-replay"};

    private static TraceEvent line(String thread, String text) {
        return new TraceEvent(Instant.now(), "op", thread, "step", "127.0.0.1", TraceLevel.INFO, text);
    }

    @Test
    void tickingSplitAfterTheLinesArrivedShowsThem() throws Exception {
        Map<String, String> panes = onEdt(() -> {
            ShellWindow shell = new ShellWindow(ShellTheme.specter(), e -> true);
            try {
                for (int i = 1; i <= 3; i++) {
                    shell.onTrace(line("attack-replay", "replay step " + i));
                    shell.onTrace(line("attack-recon", "recon step " + i));
                }
                shell.drain();
                shell.enableSplit(); // like ticking "Split threads" now
                shell.drain();
                return shell.threadPaneTexts();
            } finally {
                shell.close();
            }
        });
        assertEquals(List.of("attack-replay", "attack-recon"), List.copyOf(panes.keySet()), "one pane per thread");
        assertTrue(panes.get("attack-replay").contains("replay step 3"), panes.toString());
        assertTrue(panes.get("attack-recon").contains("recon step 1"), panes.toString());
    }

    @Test
    void theSingleCountersLinesSurviveTheSwitchToSplit() throws Exception {
        Map<String, String> panes = onEdt(() -> {
            ShellWindow shell = new ShellWindow(ShellTheme.console(), e -> true);
            try {
                shell.onTrace(line("op-mirror-replay", "mirror: honeytoken replay -> 10.10.0.7:8080"));
                shell.drain();
                shell.enableSplit(); // the simultaneous counter reuses this shell
                for (String thread : COUNTER_THREADS) {
                    shell.onTrace(line(thread, thread + " started"));
                }
                shell.drain();
                return shell.threadPaneTexts();
            } finally {
                shell.close();
            }
        });
        assertEquals(6, panes.size(), panes.keySet().toString());
        assertTrue(panes.getOrDefault("op-mirror-replay", "").contains("mirror: honeytoken replay"),
                "the single counter's pane is still there: " + panes.keySet());
    }

    /** At the counter shell's default size, all five panes are fully inside the view. */
    @Test
    void everyPaneIsOnScreenWithoutScrollingSideways() throws Exception {
        String problem = onEdt(() -> {
            ShellWindow shell = new ShellWindow(ShellTheme.console(), e -> true);
            try {
                shell.enableSplit();
                for (String thread : COUNTER_THREADS) {
                    shell.onTrace(line(thread, thread + " started: a reasonably long line of output"));
                }
                shell.drain();
                shell.setSize(860, 440);
                layOut(shell);
                JScrollPane view = shell.splitView();
                Rectangle visible = view.getViewport().getBounds();
                Container grid = (Container) view.getViewport().getView();
                if (grid.getWidth() > visible.width) {
                    return "the panes are " + grid.getWidth() + " px wide in a " + visible.width + " px view";
                }
                if (grid.getComponentCount() != COUNTER_THREADS.length) {
                    return grid.getComponentCount() + " panes";
                }
                for (Component pane : grid.getComponents()) {
                    Rectangle b = pane.getBounds();
                    if (b.width < 150 || b.height < 100 || b.x + b.width > visible.width
                            || b.y + b.height > visible.height) {
                        return "pane at " + b + " in a " + visible.width + "x" + visible.height + " view";
                    }
                }
                return null;
            } finally {
                shell.close();
            }
        });
        assertNull(problem);
    }

    /** Lay a component tree out without a native window (works headless too). */
    private static void layOut(Container c) {
        c.doLayout();
        for (Component child : c.getComponents()) {
            if (child instanceof Container k) {
                layOut(k);
            }
        }
    }

    private interface EdtCall<T> {
        T call() throws Exception;
    }

    private static <T> T onEdt(EdtCall<T> call) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(call.call());
            } catch (Exception e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }
}
