package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.console.H2Persistence;
import com.mirage.console.db.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A-07: with MIRAGE_DB_PASSWORD set, the H2 file is encrypted on disk and can't be opened
 * without the password; a mismatch refuses with a plain message instead of a stack trace.
 */
class DatabasePasswordTest {

    private static final String MARKER = "MARKER-a07-plaintext-check";

    private static void writeMarker(Database db) {
        new H2Persistence(db, "test-key").saveEvent(new AttackEvent("10.10.0.5", 4444, "http-login",
                Action.LOGIN_ATTEMPT, "admin:" + MARKER, Instant.now()));
    }

    private static boolean fileContainsMarker(Path dir) throws Exception {
        try (var files = Files.list(dir)) {
            for (Path f : files.toList()) {
                String bytes = new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1);
                if (bytes.contains(MARKER)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void withAPasswordTheFileIsEncryptedAndReopensWithIt(@TempDir Path dir) throws Exception {
        try (Database db = Database.openFile(dir, "s3cret-db")) {
            writeMarker(db);
        }
        assertFalse(fileContainsMarker(dir), "stored text must not be readable in the file");
        try (Database again = Database.openFile(dir, "s3cret-db")) {
            assertNotNull(again);
        }
    }

    @Test
    void withoutAPasswordTheFileIsPlainAsBefore(@TempDir Path dir) throws Exception {
        try (Database db = Database.openFile(dir, null)) {
            writeMarker(db);
        }
        assertTrue(fileContainsMarker(dir), "no password: unchanged behaviour (sanity check of the probe)");
    }

    @Test
    void anEncryptedFileRefusesAMissingOrWrongPasswordPlainly(@TempDir Path dir) throws Exception {
        try (Database db = Database.openFile(dir, "s3cret-db")) {
            writeMarker(db);
        }
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> Database.openFile(dir, null).close());
        assertTrue(missing.getMessage().contains(Database.PASSWORD_ENV), missing.getMessage());
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Database.openFile(dir, "nope").close());
        assertTrue(wrong.getMessage().contains(Database.PASSWORD_ENV), wrong.getMessage());
    }

    @Test
    void aPlainFileRefusesAPasswordPlainly(@TempDir Path dir) throws Exception {
        try (Database db = Database.openFile(dir, null)) {
            writeMarker(db);
        }
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Database.openFile(dir, "s3cret-db").close());
        assertTrue(e.getMessage().contains(Database.PASSWORD_ENV), e.getMessage());
    }
}
