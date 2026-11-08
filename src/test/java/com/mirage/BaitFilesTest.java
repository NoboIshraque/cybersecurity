package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.sensor.BaitFileWatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-29: no bait was ever planted, so the demo's "edit a file in bait/" had nothing to edit, and
 * files in subfolders weren't watched. A-05: there was no integrity baseline, so a change made
 * while the Sensor was down was never reported.
 */
class BaitFilesTest {

    @TempDir
    Path tmp;

    private final List<AttackEvent> events = new CopyOnWriteArrayList<>();
    private final List<BaitFileWatcher> started = new ArrayList<>();

    @AfterEach
    void stopAll() {
        started.forEach(BaitFileWatcher::stop);
    }

    private Path bait() {
        return tmp.resolve("bait");
    }

    private BaitFileWatcher start() throws Exception {
        Files.createDirectories(bait());
        BaitFileWatcher w = new BaitFileWatcher(bait(), events::add, Duration.ofMinutes(10));
        w.start();
        started.add(w);
        return w;
    }

    private void awaitEvent(Predicate<String> payload, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (events.stream().noneMatch(e -> e.action() == Action.FILE_CHANGE && payload.test(e.payload()))) {
            if (System.currentTimeMillis() > deadline) {
                fail("no FILE_CHANGE event for " + what + "; got " + events);
            }
            Thread.sleep(25);
        }
    }

    @Test
    void plantsRealisticLuresIntoAnEmptyFolderWithoutRaisingAlarms() throws Exception {
        start();
        assertTrue(Files.isRegularFile(bait().resolve("passwords.csv")));
        assertTrue(Files.isRegularFile(bait().resolve("vpn.key")));
        Path backup = bait().resolve("config").resolve("backup.cfg");
        assertTrue(Files.isRegularFile(backup));
        assertTrue(Files.readString(backup).contains("svc_backup:S3cr3tBackup!"),
                "backup.cfg carries the honeytoken the Console watches for");
        Thread.sleep(300);
        assertTrue(events.isEmpty(), "planting the lures is not tampering: " + events);
    }

    @Test
    void neverOverwritesAnExistingFile() throws Exception {
        Files.createDirectories(bait());
        Files.writeString(bait().resolve("passwords.csv"), "the user's own content");
        start();
        assertEquals("the user's own content", Files.readString(bait().resolve("passwords.csv")));
    }

    @Test
    void aChangeInsideASubfolderIsReported() throws Exception {
        start();
        Files.writeString(bait().resolve("config").resolve("backup.cfg"), "ransomware was here");
        awaitEvent(p -> p.contains("config/backup.cfg"), "config/backup.cfg");
    }

    @Test
    void changesMadeWhileTheSensorWasDownAreReportedAtTheNextStart() throws Exception {
        start().stop();
        Files.writeString(bait().resolve("vpn.key"), "swapped key");
        Files.delete(bait().resolve("passwords.csv"));
        events.clear();

        start();
        awaitEvent(p -> p.contains("ENTRY_MODIFY vpn.key") && p.contains("while the Sensor was down"), "vpn.key");
        awaitEvent(p -> p.contains("ENTRY_DELETE passwords.csv") && p.contains("while the Sensor was down"),
                "passwords.csv");
        assertTrue(Files.isRegularFile(bait().resolve("passwords.csv")), "the missing lure is planted again");
    }

    @Test
    void savingTheSameContentAgainIsNotReportedTwice() throws Exception {
        BaitFileWatcher w = start();
        Path key = bait().resolve("vpn.key");
        Files.writeString(key, "edited once");
        awaitEvent(p -> p.contains("vpn.key"), "vpn.key");
        Files.writeString(key, "edited once"); // same bytes again, like an editor's extra save
        Thread.sleep(500);
        w.checkNow();
        assertEquals(1, events.stream().filter(e -> e.payload().contains("vpn.key")).count(), events.toString());
    }
}
