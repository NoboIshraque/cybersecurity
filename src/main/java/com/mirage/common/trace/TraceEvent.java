package com.mirage.common.trace;

import java.time.Instant;

/**
 * One line of "what the system is doing right now", for the process shells.
 *
 * @param time    when it happened
 * @param channel which shell family it belongs to (e.g. {@code console}, {@code op}, {@code attack}, {@code watch})
 * @param thread  the name of the thread that did the work (so the shell can colour and lane it)
 * @param stage   the step inside the channel (e.g. {@code rx}, {@code rule}, {@code ledger}, or a strategy name)
 * @param subject the source address the line is about, or {@code null} when it is not about one
 * @param level   how to render it
 * @param text    the human-readable line
 */
public record TraceEvent(Instant time, String channel, String thread, String stage,
                         String subject, TraceLevel level, String text) {

    /** A line stamped with the current time and the calling thread's name. */
    public static TraceEvent now(String channel, String stage, String subject, TraceLevel level, String text) {
        return new TraceEvent(Instant.now(), channel, Thread.currentThread().getName(),
                stage, subject, level, text);
    }
}
