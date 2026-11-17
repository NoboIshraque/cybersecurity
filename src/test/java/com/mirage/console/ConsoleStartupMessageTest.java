package com.mirage.console;

import com.mirage.console.db.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.BindException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-12: a second Console in the same folder, or one whose port is taken, says so in one line
 * the operator can act on, instead of an H2 or socket stack trace.
 */
class ConsoleStartupMessageTest {

    /** The real case is two processes: a JVM shares its own open database, so lock it from a child. */
    @Test
    void aSecondConsoleInTheSameFolderIsToldWhy(@TempDir Path dir) throws Exception {
        Process holder = HoldDatabase.start(dir);
        try (BufferedReader out = new BufferedReader(new InputStreamReader(holder.getInputStream()))) {
            assertEquals("open", out.readLine(), "the child JVM holds the database");
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> Database.openFile(dir, null).close());
            assertTrue(e.getMessage().contains("already running"), e.getMessage());
            assertFalse(e.getMessage().contains("Jdbc"), "no H2 internals in the message: " + e.getMessage());
        } finally {
            holder.destroyForcibly().waitFor();
        }
    }

    @Test
    void operatorFixableFailuresBecomeOneLine() {
        assertEquals("port taken", ConsoleMain.startupProblem(new BindException("port taken")));
        assertEquals("unsafe range", ConsoleMain.startupProblem(new IllegalArgumentException("unsafe range")));
        assertEquals("locked", ConsoleMain.startupProblem(new IllegalStateException("locked")));
    }

    @Test
    void aRealBugStillShowsItsStackTrace() {
        assertNull(ConsoleMain.startupProblem(new NullPointerException("bug")));
    }
}
