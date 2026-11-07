package com.mirage.common.trace;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Observer hub for trace lines, the same shape as the Console's AlertBus. Worker
 * threads emit; every subscribed shell receives each line and filters what it shows.
 * A listener that throws is isolated so it cannot break the thread doing real work.
 */
public final class TraceBus implements TraceSink {

    private final CopyOnWriteArrayList<Consumer<TraceEvent>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<TraceEvent> listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Consumer<TraceEvent> listener) {
        listeners.remove(listener);
    }

    @Override
    public void emit(TraceEvent event) {
        for (Consumer<TraceEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                // a broken display must never stop the pipeline
            }
        }
    }

    public int listenerCount() {
        return listeners.size();
    }
}
