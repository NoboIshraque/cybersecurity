package com.mirage.specter;

import com.mirage.common.ScopeGuard;
import com.mirage.common.trace.TraceLevel;
import com.mirage.common.trace.TraceSink;

import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Drives a {@link Campaign} against ONE lab target. The target is checked by the
 * same {@link ScopeGuard} the defence side uses (synchronously, before anything
 * runs), so the red team can only ever reach the lab's own decoys. A stealth
 * delay between strategies models low-and-slow versus fast-and-loud.
 *
 * <p>Each strategy reports its steps through an {@link AttackTrace}; the Attacker
 * binds that to a {@link TraceSink} under a channel (e.g. {@code attack} for a
 * Specter campaign, {@code counter} when the Console turns a technique back on a
 * source), so the right shell shows the work.
 */
public final class Attacker {

    private final ScopeGuard guard;
    private final Consumer<String> logger;
    /** How long an idle attack thread waits for more work before it exits (B-33). */
    public static final Duration IDLE_THREAD_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Up to 16 attack threads. B-33: they are daemons and exit when idle, so the Attacker that
     * each Specter launch creates can't pile up live threads that keep the JVM running.
     */
    private final ThreadPoolExecutor pool;
    private volatile TraceSink trace = TraceSink.NONE;
    private volatile String channel = "attack";

    public Attacker(ScopeGuard guard) {
        this(guard, message -> { });
    }

    public Attacker(ScopeGuard guard, Consumer<String> logger) {
        this(guard, logger, IDLE_THREAD_TIMEOUT);
    }

    public Attacker(ScopeGuard guard, Consumer<String> logger, Duration idleThreadTimeout) {
        this.guard = guard;
        this.logger = logger;
        this.pool = new ThreadPoolExecutor(16, 16, idleThreadTimeout.toMillis(), TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), r -> {
                    Thread t = new Thread(r, "attacker");
                    t.setDaemon(true);
                    return t;
                });
        pool.allowCoreThreadTimeOut(true);
    }

    /** Send per-step traces to {@code trace} under {@code channel} (default: none / "attack"). */
    public void setTrace(TraceSink trace, String channel) {
        this.trace = trace == null ? TraceSink.NONE : trace;
        this.channel = channel == null ? "attack" : channel;
    }

    public void launch(Campaign campaign, Target target) {
        launch(campaign, target, Duration.ZERO);
    }

    public void launch(Campaign campaign, Target target, Duration stealthDelay) {
        guard.require(target.host()); // safety boundary, on the caller's thread
        // execute, not submit: anything that still escapes reaches the thread's uncaught-exception
        // handler (printed) instead of being parked in a Future nobody reads (B-20).
        pool.execute(() -> runCampaign(campaign, target, stealthDelay));
    }

    /**
     * Launch every strategy of the campaign at once, each on its own pool thread,
     * so the target faces several techniques simultaneously instead of one after
     * another. ScopeGuard is still checked once, on the caller's thread, before any
     * thread is started, so an out-of-range target is refused before anything runs.
     */
    public void launchConcurrent(Campaign campaign, Target target) {
        guard.require(target.host()); // safety boundary, before any thread starts
        for (AttackStrategy strategy : campaign.strategies()) {
            pool.execute(() -> runOne(strategy, target, true));
        }
        logger.accept(campaign.strategies().size() + " strategies launched concurrently against "
                + target.host() + ":" + target.port());
    }

    private void runCampaign(Campaign campaign, Target target, Duration stealthDelay) {
        Thread.currentThread().setName(channel + "-" + campaign.name());
        for (AttackStrategy strategy : campaign.strategies()) {
            runOne(strategy, target, false);
            if (!stealthDelay.isZero()) {
                sleepQuietly(stealthDelay);
            }
        }
        logger.accept("campaign '" + campaign.name() + "' complete");
        trace.line(channel, campaign.name(), target.host(), TraceLevel.INFO,
                "campaign '" + campaign.name() + "' complete");
    }

    private void runOne(AttackStrategy strategy, Target target, boolean ownThread) {
        if (ownThread) {
            Thread.currentThread().setName(channel + "-" + strategy.name());
        }
        String where = target.host() + ":" + target.port();
        logger.accept("running " + strategy.name() + " against " + where);
        AttackTrace t = (level, text) ->
                trace.line(channel, strategy.name(), target.host(), level, text);
        t.line(TraceLevel.INFO, "start " + strategy.name() + " -> " + where);
        try {
            strategy.run(target, t);
        } catch (RuntimeException e) {
            // B-20: a bug in one strategy must end its shell visibly, not vanish in the pool.
            String reason = e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage());
            logger.accept(strategy.name() + " against " + where + " failed: " + reason);
            t.line(TraceLevel.BAD, strategy.name() + " stopped by an error: " + reason);
            t.line(TraceLevel.BANNER, "ERROR — " + strategy.name().toUpperCase() + " FAILED");
            return;
        }
        t.line(TraceLevel.INFO, strategy.name() + " finished");
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
