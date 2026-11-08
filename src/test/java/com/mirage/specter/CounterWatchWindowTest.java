package com.mirage.specter;

import com.mirage.common.trace.TraceBus;
import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * FB-19: closing the counter-watch window left its listener running with nothing on screen, so a
 * later counter-attack was caught but never shown. Closing now hides the window, the listener
 * keeps running, and the next hit brings the window back (like the defender's threat popups).
 */
class CounterWatchWindowTest {

    private static TraceEvent line(String channel, String stage, TraceLevel level, String text) {
        return TraceEvent.now(channel, stage, "10.10.0.1", level, text);
    }

    /** Only an actual inbound connection reopens the window; its own start-up line does not. */
    @Test
    void aHitReopensTheWindowButOtherLinesDoNot() {
        assertTrue(CounterWatchWindow.reopensOn(line("watch", "incoming", TraceLevel.BAD,
                "INCOMING connection #2 from 10.10.0.1:50000 on :8080")), "an incoming connection");
        assertTrue(CounterWatchWindow.reopensOn(line("watch", "incoming", TraceLevel.BANNER,
                "INCOMING COUNTER-ATTACK")), "the first-hit banner");
        assertFalse(CounterWatchWindow.reopensOn(line("watch", "listen", TraceLevel.INFO,
                "counter-watch listening on :8080 (accept-and-log only)")), "the start-up line");
        assertFalse(CounterWatchWindow.reopensOn(line("attack", "incoming", TraceLevel.BAD,
                "not the watch channel")), "another channel");
    }

    /** The real thing: close the window, keep listening, and pop back up when a counter lands. */
    @Test
    void closingHidesTheWindowAndTheNextHitBringsItBack() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        TraceBus bus = new TraceBus();
        CounterWatchWindow window = new CounterWatchWindow();
        bus.subscribe(window::onTrace);
        CounterWatch watch = new CounterWatch(0, bus);
        int port = watch.start();
        try {
            SwingUtilities.invokeAndWait(window::show);
            assertTrue(onEdt(() -> window.frame().isShowing()), "shown at start");

            SwingUtilities.invokeAndWait(() -> window.frame().dispatchEvent(
                    new WindowEvent(window.frame(), WindowEvent.WINDOW_CLOSING)));
            assertFalse(onEdt(() -> window.frame().isShowing()), "closing hides it");

            try (Socket counter = new Socket()) {
                counter.connect(new InetSocketAddress("127.0.0.1", port), 2000); // still listening
            }
            long deadline = System.currentTimeMillis() + 5000;
            while (!onEdt(() -> window.frame().isShowing()) && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertTrue(onEdt(() -> window.frame().isShowing()), "the next hit brings the window back");
            assertEquals(1, watch.hits());
        } finally {
            watch.stop();
            SwingUtilities.invokeAndWait(() -> window.frame().dispose());
        }
    }

    private static boolean onEdt(java.util.function.BooleanSupplier check) throws Exception {
        AtomicBoolean result = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> result.set(check.getAsBoolean()));
        return result.get();
    }
}
