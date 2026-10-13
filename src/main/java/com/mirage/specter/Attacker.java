package com.mirage.specter;

import com.mirage.common.ScopeGuard;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Drives attack strategies concurrently against ONE lab target. It passes
 * every target through the same {@link ScopeGuard} the defence side uses, so
 * the red team can only ever reach the lab's own decoys.
 */
public final class Attacker {

    private final ScopeGuard guard;
    private final ExecutorService pool = Executors.newFixedThreadPool(16);

    public Attacker(ScopeGuard guard) {
        this.guard = guard;
    }

    public void launch(List<AttackStrategy> strategies, Target target) {
        guard.require(target.host()); // same safety boundary as the defence side
        for (AttackStrategy strategy : strategies) {
            pool.submit(() -> strategy.run(target));
        }
    }

    public void shutdown() {
        pool.shutdown();
    }
}
