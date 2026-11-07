package com.mirage.console;

import com.mirage.common.trace.TraceEvent;

import java.util.function.Predicate;

/**
 * Which trace lines each Console shell shows. Kept apart from the Swing code so
 * the routing is unit-tested.
 */
public final class ShellFilters {

    private ShellFilters() {
    }

    /** The whole Console pipeline (receive threads, engine, rules, ledger, responses, ACKs). */
    public static Predicate<TraceEvent> pipeline() {
        return e -> "console".equals(e.channel());
    }

    /** The threat popup: the pipeline lines about one source. */
    public static Predicate<TraceEvent> threat(String sourceIp) {
        return e -> "console".equals(e.channel()) && sourceIp.equals(e.subject());
    }

    /**
     * A defensive operation shell (tarpit / fake data / block / unblock) for one
     * source: the operator's command and ScopeGuard check, then the broadcast to
     * the Sensors and each Sensor's ACK.
     */
    public static Predicate<TraceEvent> defence(String sourceIp) {
        return e -> sourceIp.equals(e.subject())
                && (("op".equals(e.channel()) && "command".equals(e.stage()))
                || ("console".equals(e.channel()) && ("dispatch".equals(e.stage()) || "ack".equals(e.stage()))));
    }

    /** A counter operation shell for one source: every counter line and every counter thread's steps. */
    public static Predicate<TraceEvent> counter(String sourceIp) {
        return e -> "op".equals(e.channel()) && sourceIp.equals(e.subject()) && !"command".equals(e.stage());
    }
}
