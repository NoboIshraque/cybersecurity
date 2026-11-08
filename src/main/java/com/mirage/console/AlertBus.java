package com.mirage.console;

import com.mirage.common.Alert;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Observer hub: the dashboard and any other listener subscribe for new alerts. */
public final class AlertBus {

    private final CopyOnWriteArrayList<Consumer<Alert>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<Alert> listener) {
        listeners.add(listener);
    }

    /**
     * Deliver to every listener. One listener's exception (say, a dashboard bug) is reported and
     * does not reach the others or the publisher, which is the engine thread (B-33, as TraceBus).
     */
    public void publish(Alert alert) {
        for (Consumer<Alert> listener : listeners) {
            try {
                listener.accept(alert);
            } catch (RuntimeException e) {
                System.err.println("[alert-bus] a listener failed on " + alert.canonical() + ": " + e);
            }
        }
    }
}
