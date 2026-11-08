package com.mirage.specter;

import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;
import com.mirage.ui.shell.ShellTheme;
import com.mirage.ui.shell.ShellWindow;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * The attacker's "INCOMING" window: shows what {@link CounterWatch} reports. Closing it only hides
 * it; the listener keeps running, and the next counter-attack brings the window back (FB-19), the
 * same way the defender's threat popups re-show on the next escalation. So a stray click on X
 * during the demo can never hide a counter that landed.
 */
public final class CounterWatchWindow {

    static final String TITLE = "Specter — counter-watch (incoming)";

    private final ShellWindow shell = new ShellWindow(ShellTheme.specter(), e -> true);
    private final JFrame frame = new JFrame(TITLE);

    public CounterWatchWindow() {
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE); // hide, not dispose: it comes back on a hit
        frame.add(shell);
        frame.setSize(760, 440);
        frame.setLocationByPlatform(true);
    }

    /** True for a line that means a connection came in: the per-hit line or the first-hit banner. */
    static boolean reopensOn(TraceEvent event) {
        return "watch".equals(event.channel()) && "incoming".equals(event.stage())
                && (event.level() == TraceLevel.BAD || event.level() == TraceLevel.BANNER);
    }

    /** Register on the TraceBus. Any thread: the line is queued, and a reopen goes to the EDT. */
    public void onTrace(TraceEvent event) {
        shell.onTrace(event);
        if (reopensOn(event)) {
            SwingUtilities.invokeLater(() -> {
                if (!frame.isVisible()) {
                    show();
                }
            });
        }
    }

    /** Show the window and bring it to the front (on the EDT). */
    public void show() {
        frame.setVisible(true);
        frame.toFront();
    }

    JFrame frame() {
        return frame;
    }

    ShellWindow shell() {
        return shell;
    }
}
