package com.mirage.console.ui;

import com.mirage.common.trace.TraceBacklog;
import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;
import com.mirage.console.ShellFilters;
import com.mirage.ui.shell.ShellTheme;
import com.mirage.ui.shell.ShellWindow;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The live {@link OperationRunner}: before an operator action runs, it opens (or
 * brings forward) an operation shell for that source, then runs the work on a
 * worker thread named after the operation ({@code op-block}, {@code op-counter-all}, …).
 * The shell shows the ScopeGuard check, the dispatch, each Sensor's ACK and — for
 * a counter — every counter thread's steps; a simultaneous counter opens split
 * into one pane per thread.
 *
 * <p>Each source has two shells: one for defensive commands and one for counters.
 * Shells are created and looked up on the Swing thread only.
 */
public final class OperationShells implements OperationRunner {

    private record Open(JFrame frame, ShellWindow shell) {
    }

    private final TraceBacklog backlog;
    private final BiFunction<Operation, String, String> work;
    private final Map<String, Open> open = new HashMap<>();
    private final AtomicInteger workerCount = new AtomicInteger();
    private final ExecutorService workers = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "op-worker-" + workerCount.incrementAndGet());
        t.setDaemon(true);
        return t;
    });
    private int windowsOpened;

    /**
     * @param backlog the Console's trace fan-out
     * @param work    does the operation and returns its result line (runs on a worker thread)
     */
    public OperationShells(TraceBacklog backlog, BiFunction<Operation, String, String> work) {
        this.backlog = backlog;
        this.work = work;
    }

    @Override
    public void run(Operation operation, String sourceIp, Consumer<String> onResult) {
        ShellWindow shell = shellFor(operation, sourceIp);
        if (operation == Operation.COUNTER_ALL) {
            shell.enableSplit();
        }
        workers.submit(() -> {
            Thread self = Thread.currentThread();
            String pooledName = self.getName();
            self.setName("op-" + operation.name().toLowerCase(Locale.ROOT).replace('_', '-'));
            String result;
            try {
                result = work.apply(operation, sourceIp);
            } catch (RuntimeException e) {
                result = "failed: " + e.getMessage();
            }
            shell.onTrace(banner(operation, sourceIp, result)); // tagged with the op thread
            self.setName(pooledName);
            String line = result;
            SwingUtilities.invokeLater(() -> onResult.accept(line));
        });
    }

    private ShellWindow shellFor(Operation operation, String sourceIp) {
        String key = sourceIp + (operation.isCounter() ? "#counter" : "#defence");
        Open existing = open.get(key);
        if (existing != null && existing.frame().isDisplayable()) {
            existing.frame().toFront();
            return existing.shell();
        }
        Predicate<TraceEvent> filter = operation.isCounter()
                ? ShellFilters.counter(sourceIp) : ShellFilters.defence(sourceIp);
        ShellWindow shell = new ShellWindow(ShellTheme.console(), filter);
        Consumer<TraceEvent> feed = shell::onTrace;
        backlog.attach(feed, false); // attach before the work starts, so nothing is missed

        JFrame frame = new JFrame("Mirage op shell — "
                + (operation.isCounter() ? "counter" : "defence") + " → " + sourceIp);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.add(shell);
        frame.setSize(880, 520);
        int offset = 40 * (windowsOpened++ % 6);
        frame.setLocation(140 + offset, 120 + offset);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                backlog.detach(feed);
                shell.close();
                open.remove(key);
            }
        });
        open.put(key, new Open(frame, shell));
        frame.setVisible(true);
        return shell;
    }

    /** A closing banner for this shell only (not broadcast on the bus). */
    private static TraceEvent banner(Operation operation, String sourceIp, String result) {
        return new TraceEvent(Instant.now(), "op", Thread.currentThread().getName(),
                operation.isCounter() ? "counter" : "command", sourceIp, TraceLevel.BANNER,
                bannerText(operation, result));
    }

    /** The closing banner's text for an operation and its result line. */
    static String bannerText(Operation operation, String result) {
        if (result.contains("REFUSED")) {
            return "REFUSED — OUTSIDE LAB RANGE";
        }
        if (result.startsWith("failed") || result.startsWith("no ")
                || result.contains("not configured") || result.contains("  no counter mapped")) {
            return operation.label().toUpperCase(Locale.ROOT) + " — NOT SENT";
        }
        if (operation.isCounter()) {
            return operation == Operation.COUNTER_ALL ? "COUNTERS LAUNCHED (CONCURRENT)" : "COUNTER LAUNCHED";
        }
        // FB-10: written once the work returns, while the Sensor's ACK arrives on its own thread,
        // so the ACK can be shown first. "Awaiting" would then be false; the ACK line confirms.
        return operation.label().toUpperCase(Locale.ROOT) + " DISPATCHED";
    }
}
