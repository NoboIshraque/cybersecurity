package com.mirage.common.trace;

/**
 * Where trace lines go. Components take a sink and default to {@link #NONE}, so tests
 * and headless runs pay nothing; the GUIs pass a {@link TraceBus}.
 */
@FunctionalInterface
public interface TraceSink {

    void emit(TraceEvent event);

    /** Convenience: stamp time and thread, then emit. */
    default void line(String channel, String stage, String subject, TraceLevel level, String text) {
        emit(TraceEvent.now(channel, stage, subject, level, text));
    }

    TraceSink NONE = event -> { };
}
