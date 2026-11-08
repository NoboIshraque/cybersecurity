package com.mirage;

import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;
import com.mirage.ui.shell.ShellTheme;
import com.mirage.ui.shell.ShellWindow;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-16: the thread strip must follow each thread's state. It used to rebuild only when the
 * number of threads changed, so finished threads kept showing "•run" next to the end banner.
 */
class ShellStripTest {

    private static TraceEvent line(String thread, String text) {
        return new TraceEvent(Instant.now(), "attack", thread, "stage", "10.10.0.5", TraceLevel.INFO, text);
    }

    private static List<String> labelsAfterDrain(ShellWindow shell) throws Exception {
        Thread.sleep(600); // the shell's own 45 ms timer drains the lines on the EDT
        AtomicReference<List<String>> labels = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> labels.set(shell.stripLabels()));
        return labels.get();
    }

    @Test
    void finishedThreadsShowDoneWithoutANewThreadAppearing() throws Exception {
        AtomicReference<ShellWindow> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> ref.set(new ShellWindow(ShellTheme.specter(), e -> true)));
        ShellWindow shell = ref.get();

        shell.onTrace(line("attack-brute", "start brute-force"));
        shell.onTrace(line("attack-spray", "start password-spray"));
        List<String> running = labelsAfterDrain(shell);
        assertTrue(running.stream().allMatch(l -> l.endsWith("•run")), running.toString());

        shell.onTrace(line("attack-brute", "brute-force finished"));
        shell.onTrace(line("attack-spray", "password-spray finished"));
        List<String> done = labelsAfterDrain(shell);
        assertEquals(2, done.size(), done.toString());
        assertTrue(done.stream().allMatch(l -> l.endsWith("•done")), done.toString());
    }
}
