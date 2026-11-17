package com.mirage.console;

import com.mirage.console.db.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FA-01: a force-killed defender (Task Manager / {@code Stop-Process -Force}) must not block the
 * next start in the same folder. H2's lock is an OS lock on {@code mirage.mv.db}; Windows drops it
 * only once the killed JVM is fully torn down, which can be just after the operator starts again.
 * A live Console in the folder must still be refused, and quickly.
 */
class StaleDatabaseLockTest {

    private static Process holdDatabase(Path dir) throws Exception {
        Process holder = HoldDatabase.start(dir);
        BufferedReader out = new BufferedReader(new InputStreamReader(holder.getInputStream()));
        assertEquals("open", out.readLine(), "the child JVM holds the database");
        return holder;
    }

    /** The restart begins while the killed defender is still going down. */
    @Test
    void aRestartWhileTheKilledDefenderIsStillDyingWaitsForItsLock(@TempDir Path dir) throws Exception {
        Process holder = holdDatabase(dir);
        try {
            CompletableFuture<Void> kill = CompletableFuture.runAsync(holder::destroyForcibly,
                    CompletableFuture.delayedExecutor(1500, TimeUnit.MILLISECONDS));
            assertDoesNotThrow(() -> Database.openFile(dir, null).close(),
                    "the lock of a force-killed defender clears, so the next start must get in");
            kill.join();
        } finally {
            holder.destroyForcibly().waitFor();
        }
    }

    /** The plain case: the old defender is fully gone before the new one starts. */
    @Test
    void aStartAfterAForceKillOpensTheDatabase(@TempDir Path dir) throws Exception {
        Process holder = holdDatabase(dir);
        holder.destroyForcibly().waitFor();
        assertDoesNotThrow(() -> Database.openFile(dir, null).close());
    }

    /** B-12 still holds: a second Console next to a running one is refused, within seconds. */
    @Test
    void aLiveConsoleIsStillRefusedWithinFifteenSeconds(@TempDir Path dir) throws Exception {
        Process holder = holdDatabase(dir);
        try {
            long start = System.nanoTime();
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> Database.openFile(dir, null).close());
            long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);
            assertTrue(e.getMessage().contains("already running"), e.getMessage());
            assertTrue(seconds < 15, "refused after " + seconds + " s");
            assertTrue(holder.isAlive(), "the running Console's database is never taken from it");
        } finally {
            holder.destroyForcibly().waitFor();
        }
    }
}
