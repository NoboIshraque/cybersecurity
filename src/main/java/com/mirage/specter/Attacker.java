package com.mirage.specter;

import com.mirage.common.ScopeGuard;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Drives a {@link Campaign} against ONE lab target. The target is checked by the
 * same {@link ScopeGuard} the defence side uses (synchronously, before anything
 * runs), so the red team can only ever reach the lab's own decoys. A stealth
 * delay between strategies models low-and-slow versus fast-and-loud.
 */
public final class Attacker {

    private final ScopeGuard guard;
    private final Consumer<String> logger;
    private final ExecutorService pool = Executors.newFixedThreadPool(16);

    public Attacker(ScopeGuard guard) {
        this(guard, message -> { });
    }

    public Attacker(ScopeGuard guard, Consumer<String> logger) {
        this.guard = guard;
        this.logger = logger;
    }

    public void launch(Campaign campaign, Target target) {
        launch(campaign, target, Duration.ZERO);
    }

    public void launch(Campaign campaign, Target target, Duration stealthDelay) {
        guard.require(target.host()); // safety boundary, on the caller's thread
        pool.submit(() -> runCampaign(campaign, target, stealthDelay));
    }

    private void runCampaign(Campaign campaign, Target target, Duration stealthDelay) {
        for (AttackStrategy strategy : campaign.strategies()) {
            logger.accept("running " + strategy.name() + " against "
                    + target.host() + ":" + target.port());
            strategy.run(target);
            if (!stealthDelay.isZero()) {
                sleepQuietly(stealthDelay);
            }
        }
        logger.accept("campaign '" + campaign.name() + "' complete");
    }

    private static void sleepQuietly(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown() {
        pool.shutdown();
    }
}
