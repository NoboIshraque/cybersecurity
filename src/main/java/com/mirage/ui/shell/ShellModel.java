package com.mirage.ui.shell;

import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The GUI-free core of a process shell. Trace lines are fed in from any thread;
 * the window drains them a few at a time on a timer, which is what paces the
 * "typed out" effect. The model also tracks one lane per worker thread (a stable
 * colour and a running/waiting/done state) so the window can draw a thread strip
 * and, when several threads run at once, split into one pane per thread.
 *
 * <p>No Swing here, so every rule below is unit-tested directly.
 */
public final class ShellModel {

    /** How many distinct thread colours the window should cycle through. */
    public static final int PALETTE_SIZE = 6;

    /** A thread's lifecycle as the shell understands it. */
    public enum State { RUNNING, WAITING, DONE }

    /** One rendered line, with its thread's stable colour index and whether it is a banner. */
    public record Line(Instant time, String thread, String stage, String subject,
                       TraceLevel level, String text, int colorIndex, boolean banner) {
    }

    /** One worker thread's lane for the thread strip. */
    public record Lane(String thread, int colorIndex, State state) {
    }

    private final Object lock = new Object();
    private final List<Line> pending = new ArrayList<>();
    private final Map<String, Integer> colorByThread = new LinkedHashMap<>();
    private final Map<String, State> stateByThread = new LinkedHashMap<>();
    private int delivered;
    private int total;

    /** Feed one trace line in (called from worker threads). */
    public void add(TraceEvent event) {
        synchronized (lock) {
            String thread = event.thread() == null ? "main" : event.thread();
            int color = colorByThread.computeIfAbsent(thread, t -> colorByThread.size() % PALETTE_SIZE);
            boolean banner = event.level() == TraceLevel.BANNER;
            pending.add(new Line(event.time(), thread, event.stage(), event.subject(),
                    event.level(), event.text(), color, banner));
            total++;
            // Activity marks the thread running unless an explicit end marker says otherwise.
            State inferred = inferState(event, stateByThread.get(thread));
            stateByThread.put(thread, inferred);
        }
    }

    /** Take up to {@code max} not-yet-delivered lines, in order. The window calls this on its timer. */
    public List<Line> poll(int max) {
        synchronized (lock) {
            List<Line> out = new ArrayList<>();
            while (delivered < pending.size() && out.size() < max) {
                out.add(pending.get(delivered++));
            }
            return out;
        }
    }

    /** True when there are undelivered lines waiting. */
    public boolean hasPending() {
        synchronized (lock) {
            return delivered < pending.size();
        }
    }

    public int totalLines() {
        synchronized (lock) {
            return total;
        }
    }

    /** The stable colour index assigned to a thread (0 before it is first seen). */
    public int colorIndex(String thread) {
        synchronized (lock) {
            return colorByThread.getOrDefault(thread, 0);
        }
    }

    /** The lanes, in the order their threads first appeared. */
    public List<Lane> lanes() {
        synchronized (lock) {
            List<Lane> out = new ArrayList<>();
            for (Map.Entry<String, Integer> e : colorByThread.entrySet()) {
                out.add(new Lane(e.getKey(), e.getValue(),
                        stateByThread.getOrDefault(e.getKey(), State.RUNNING)));
            }
            return out;
        }
    }

    /** Threads currently RUNNING (used to decide whether to split into panes). */
    public List<String> runningThreads() {
        synchronized (lock) {
            List<String> out = new ArrayList<>();
            for (Map.Entry<String, State> e : stateByThread.entrySet()) {
                if (e.getValue() == State.RUNNING) {
                    out.add(e.getKey());
                }
            }
            return out;
        }
    }

    public State stateOf(String thread) {
        synchronized (lock) {
            return stateByThread.getOrDefault(thread, State.DONE);
        }
    }

    public void markDone(String thread) {
        synchronized (lock) {
            if (colorByThread.containsKey(thread)) {
                stateByThread.put(thread, State.DONE);
            }
        }
    }

    public void markWaiting(String thread) {
        synchronized (lock) {
            if (colorByThread.containsKey(thread)) {
                stateByThread.put(thread, State.WAITING);
            }
        }
    }

    private static State inferState(TraceEvent event, State previous) {
        String text = event.text() == null ? "" : event.text().toLowerCase();
        if (text.contains("finished") || text.contains("complete") || text.contains("closed")) {
            return State.DONE;
        }
        return State.RUNNING;
    }
}
