package com.mirage.common.trace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A bounded memory of recent trace lines that also fans them out. A shell that
 * opens <em>after</em> the work started (the threat popup opens on the first
 * alert, which is raised after the receive/engine/rule lines were emitted) can
 * attach with replay and see the lines it missed, then every live line after
 * them, with no gap and no duplicate. Subscribe it to a {@link TraceBus}:
 * {@code bus.subscribe(backlog)}.
 */
public final class TraceBacklog implements Consumer<TraceEvent> {

    private final int capacity;
    private final ArrayDeque<TraceEvent> history = new ArrayDeque<>();
    private final CopyOnWriteArrayList<Consumer<TraceEvent>> listeners = new CopyOnWriteArrayList<>();

    public TraceBacklog(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.capacity = capacity;
    }

    @Override
    public synchronized void accept(TraceEvent event) {
        history.addLast(event);
        while (history.size() > capacity) {
            history.removeFirst();
        }
        for (Consumer<TraceEvent> listener : listeners) {
            deliver(listener, event);
        }
    }

    /** Attach a listener; with {@code replay}, it first receives the remembered lines, oldest first. */
    public synchronized void attach(Consumer<TraceEvent> listener, boolean replay) {
        if (replay) {
            for (TraceEvent event : history) {
                deliver(listener, event);
            }
        }
        listeners.add(listener);
    }

    public void detach(Consumer<TraceEvent> listener) {
        listeners.remove(listener);
    }

    public synchronized List<TraceEvent> history() {
        return new ArrayList<>(history);
    }

    public int listenerCount() {
        return listeners.size();
    }

    private static void deliver(Consumer<TraceEvent> listener, TraceEvent event) {
        try {
            listener.accept(event);
        } catch (RuntimeException e) {
            // a broken display must never stop the pipeline
        }
    }
}
