package com.mirage.sensor;

import java.time.Duration;

/**
 * How much a {@link Decoy} will give any client (B-06, B-07, A-04): how long it waits for a
 * line, how many connections one source may hold, how many connections can wait for a worker,
 * and how a tarpit drips and how long it holds. Defaults suit the demo; tests shrink them.
 *
 * @param readTimeout   longest wait for the client's next line before the decoy hangs up
 * @param maxPerSource  connections one source may have open at once (served + tarpitted)
 * @param workers       threads serving normal interactions
 * @param queue         connections that may wait for a worker before new ones are refused
 * @param tarpitDrip    pause between the bytes a tarpit sends
 * @param tarpitMaxHold longest a tarpit keeps a connection before closing it
 * @param maxTarpitted  tarpitted connections held at once, across all sources
 */
public record DecoyLimits(Duration readTimeout, int maxPerSource, int workers, int queue,
                          Duration tarpitDrip, Duration tarpitMaxHold, int maxTarpitted) {

    public static final DecoyLimits DEFAULT = new DecoyLimits(
            Duration.ofSeconds(15), 8, 12, 64, Duration.ofSeconds(5), Duration.ofMinutes(2), 64);

    public DecoyLimits {
        if (readTimeout.isNegative() || readTimeout.isZero() || tarpitDrip.isNegative() || tarpitDrip.isZero()
                || tarpitMaxHold.isNegative() || maxPerSource < 1 || workers < 1 || queue < 1 || maxTarpitted < 1) {
            throw new IllegalArgumentException("invalid decoy limits: " + this);
        }
    }
}
