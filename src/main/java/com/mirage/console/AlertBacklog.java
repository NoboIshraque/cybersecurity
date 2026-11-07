package com.mirage.console;

import com.mirage.common.Alert;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Holds the alerts raised before the dashboard is up (B-33): the dashboard is built on the
 * Swing thread after the Console is already listening, so alerts in that gap went to the ledger
 * but never into the feed or a popup. Subscribe this to the {@link AlertBus} first; when the
 * dashboard {@link #attach attaches}, it gets the held alerts, in order, then every new one.
 * Like {@link com.mirage.common.trace.TraceBacklog}, but for alerts.
 */
public final class AlertBacklog implements Consumer<Alert> {

    private final int capacity;
    private final Deque<Alert> held = new ArrayDeque<>();
    private Consumer<Alert> listener;

    public AlertBacklog(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public synchronized void accept(Alert alert) {
        if (listener != null) {
            listener.accept(alert);
            return;
        }
        held.addLast(alert);
        if (held.size() > capacity) {
            held.removeFirst();
        }
    }

    /** Replay the held alerts to {@code listener}, then forward new ones (no gap, no repeat). */
    public synchronized void attach(Consumer<Alert> listener) {
        for (Alert alert : held) {
            listener.accept(alert);
        }
        held.clear();
        this.listener = listener;
    }
}
