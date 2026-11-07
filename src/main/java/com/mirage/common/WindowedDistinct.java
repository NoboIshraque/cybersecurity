package com.mirage.common;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * How many distinct keys were seen within a sliding time window (B-28). Each hit is recorded
 * once and forgotten once it is older than the window, so an add is amortised O(1): the rules
 * stay constant-time (B-02). A burst can hold at most {@link #MAX_HITS} hits.
 */
final class WindowedDistinct {

    static final int MAX_HITS = 10_000;

    private record Hit(String key, Instant at) {
    }

    private final Duration window;
    private final Deque<Hit> hits = new ArrayDeque<>();
    private final Map<String, Integer> counts = new HashMap<>();

    WindowedDistinct(Duration window) {
        this.window = window;
    }

    /**
     * Record {@code key} seen at {@code at}, forget what is older than {@code now - window},
     * and return how many distinct keys are left. A hit already older than the window is not
     * recorded (an event that arrived late).
     */
    int add(String key, Instant at, Instant now) {
        Instant cutoff = now.minus(window);
        if (!at.isBefore(cutoff)) {
            hits.addLast(new Hit(key, at));
            counts.merge(key, 1, Integer::sum);
        }
        while (!hits.isEmpty() && (hits.peekFirst().at().isBefore(cutoff) || hits.size() > MAX_HITS)) {
            Hit old = hits.pollFirst();
            counts.computeIfPresent(old.key(), (k, n) -> n <= 1 ? null : n - 1);
        }
        return counts.size();
    }
}
