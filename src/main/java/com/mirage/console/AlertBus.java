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

    public void publish(Alert alert) {
        listeners.forEach(l -> l.accept(alert));
    }
}
