package com.mirage.sensor;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Plants and guards the bait files. Any change to one emits a FILE_CHANGE event, an early
 * warning of tampering or ransomware. Bait-file events have no network source, so they use
 * the sentinel "local-filesystem"; the payload is {@code <kind> <path relative to the folder>}.
 *
 * <p>On {@link #start()} (B-29, A-05):
 * <ol>
 *   <li>the folder is compared with the SHA-256 baseline saved by the last run (in
 *       {@code <folder>.sha256} next to it), so a file changed, added or removed while the
 *       Sensor was down is reported;</li>
 *   <li>missing lures are planted ({@code passwords.csv}, {@code vpn.key},
 *       {@code config/backup.cfg} carrying the honeytoken); existing files are never touched;</li>
 *   <li>a fresh baseline is taken and saved, and the folder is watched, subfolders included.</li>
 * </ol>
 * A change is reported only when a file's content differs from the baseline, which also folds
 * the several MODIFY events one save produces on Windows into one. The whole folder is
 * re-checked against the baseline every {@code rescanEvery}, and at once when the watch service
 * overflows, so a change the watcher missed is still caught.
 *
 * <p>All baseline state is confined to one worker thread; the watch thread only hands over.
 */
public final class BaitFileWatcher {

    public static final Duration DEFAULT_RESCAN = Duration.ofSeconds(30);
    /** Wait this long after a change before hashing, so a save in progress is read complete. */
    private static final long SETTLE_MS = 200;

    /** The lures planted when missing: path relative to the bait folder → content. */
    static final Map<String, String> LURES = lures();

    private final Path root;
    private final Consumer<AttackEvent> sink;
    private final Duration rescanEvery;
    private final Path baselineFile;
    private final Map<String, String> baseline = new HashMap<>(); // worker thread only
    private final Set<String> pending = new HashSet<>();          // worker thread only
    private final Map<WatchKey, Path> dirs = new HashMap<>();     // worker thread only
    private ScheduledExecutorService worker;
    private WatchService watcher;
    private volatile boolean running;

    public BaitFileWatcher(Path directory, Consumer<AttackEvent> sink) {
        this(directory, sink, DEFAULT_RESCAN);
    }

    public BaitFileWatcher(Path directory, Consumer<AttackEvent> sink, Duration rescanEvery) {
        this.root = directory.toAbsolutePath().normalize();
        this.sink = sink;
        this.rescanEvery = rescanEvery;
        this.baselineFile = root.resolveSibling(root.getFileName() + ".sha256");
    }

    public void start() throws IOException {
        Files.createDirectories(root);
        Map<String, String> saved = loadSavedBaseline();
        Map<String, String> now = scan();
        if (saved != null) {
            report(saved, now, "while the Sensor was down");
        }
        plantMissingLures();
        baseline.putAll(scan());
        saveBaseline();

        watcher = root.getFileSystem().newWatchService();
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bait-check");
            t.setDaemon(true);
            return t;
        });
        running = true;
        try {
            worker.submit(() -> {
                registerTree(root);
                return null;
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while starting the bait watcher", e);
        } catch (ExecutionException e) {
            throw new IOException("could not watch " + root, e.getCause());
        }
        long every = rescanEvery.toMillis();
        worker.scheduleWithFixedDelay(() -> rescan("found by integrity check"), every, every, TimeUnit.MILLISECONDS);
        Thread t = new Thread(this::watchLoop, "bait-watcher");
        t.setDaemon(true);
        t.start();
    }

    /** Re-check the whole folder against the baseline now, and wait for it to finish. */
    public void checkNow() {
        if (worker == null) {
            return;
        }
        try {
            // after any pending settle-delayed checks, so they are not reported twice
            worker.schedule(() -> rescan("found by integrity check"), SETTLE_MS * 2, TimeUnit.MILLISECONDS).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // a failed check is retried by the next scheduled one
        }
    }

    public void stop() {
        running = false;
        if (worker != null) {
            worker.shutdownNow();
        }
        if (watcher != null) {
            try {
                watcher.close(); // ends the watch loop
            } catch (IOException e) {
                // already closed
            }
        }
    }

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                return;
            }
            List<WatchEvent<?>> events = key.pollEvents();
            key.reset();
            try {
                worker.execute(() -> handle(key, events));
            } catch (RuntimeException stopped) {
                return;
            }
        }
    }

    /** Worker thread. Path-level work is delayed by {@link #SETTLE_MS} and merged per path. */
    private void handle(WatchKey key, List<WatchEvent<?>> events) {
        Path dir = dirs.get(key);
        for (WatchEvent<?> event : events) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW || dir == null) {
                rescan("found by integrity check (watcher overflowed)");
                continue;
            }
            Path changed = dir.resolve((Path) event.context());
            if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed)) {
                registerTree(changed);
            }
            String rel = relative(changed);
            if (pending.add(rel)) {
                worker.schedule(() -> settle(rel), SETTLE_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    /** Worker thread: compare one path (file, or a folder and everything under it) with the baseline. */
    private void settle(String rel) {
        pending.remove(rel);
        Path path = root.resolve(rel);
        Map<String, String> before = new LinkedHashMap<>();
        baseline.forEach((k, v) -> {
            if (k.equals(rel) || k.startsWith(rel + "/")) {
                before.put(k, v);
            }
        });
        Map<String, String> after = Files.isDirectory(path) ? scan(path) : hashOne(rel);
        report(before, after, null);
        before.keySet().forEach(baseline::remove);
        baseline.putAll(after);
        saveBaseline();
    }

    /** Worker thread: the whole folder against the baseline. */
    private void rescan(String why) {
        Map<String, String> now = scan();
        report(baseline, now, why);
        baseline.clear();
        baseline.putAll(now);
        saveBaseline();
        registerTree(root); // pick up any folder the watcher missed
    }

    /** One event per added, removed or changed file between {@code before} and {@code after}. */
    private void report(Map<String, String> before, Map<String, String> after, String why) {
        Set<String> paths = new TreeSet<>(before.keySet());
        paths.addAll(after.keySet());
        for (String rel : paths) {
            String was = before.get(rel);
            String is = after.get(rel);
            String kind = was == null ? "ENTRY_CREATE" : is == null ? "ENTRY_DELETE"
                    : was.equals(is) ? null : "ENTRY_MODIFY";
            if (kind != null) {
                sink.accept(new AttackEvent("local-filesystem", 0, "bait-file", Action.FILE_CHANGE,
                        kind + " " + rel + (why == null ? "" : " (" + why + ")"), Instant.now()));
            }
        }
    }

    private void registerTree(Path top) {
        try (Stream<Path> walk = Files.walk(top)) {
            for (Path dir : (Iterable<Path>) walk.filter(Files::isDirectory)::iterator) {
                if (!dirs.containsValue(dir)) {
                    dirs.put(dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE), dir);
                }
            }
        } catch (IOException | RuntimeException e) {
            // a folder vanished mid-walk; the next rescan registers what is there
        }
    }

    private void plantMissingLures() throws IOException {
        for (Map.Entry<String, String> lure : LURES.entrySet()) {
            Path file = root.resolve(lure.getKey());
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, lure.getValue(), StandardCharsets.UTF_8);
            }
        }
    }

    private Map<String, String> scan() {
        return scan(root);
    }

    private Map<String, String> scan(Path top) {
        Map<String, String> out = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(top)) {
            for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                String hash = sha256(file);
                if (hash != null) {
                    out.put(relative(file), hash);
                }
            }
        } catch (IOException | RuntimeException e) {
            // missing folder: nothing in it
        }
        return out;
    }

    private Map<String, String> hashOne(String rel) {
        String hash = Files.isRegularFile(root.resolve(rel)) ? sha256(root.resolve(rel)) : null;
        return hash == null ? Map.of() : Map.of(rel, hash);
    }

    private String relative(Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static String sha256(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            return null; // gone or locked: treated as absent until it can be read
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@code null} on the first run (no saved baseline yet). */
    private Map<String, String> loadSavedBaseline() {
        if (!Files.isRegularFile(baselineFile)) {
            return null;
        }
        Map<String, String> saved = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(baselineFile, StandardCharsets.UTF_8)) {
                int split = line.indexOf("  ");
                if (split > 0) {
                    saved.put(line.substring(split + 2), line.substring(0, split));
                }
            }
        } catch (IOException e) {
            return null;
        }
        return saved;
    }

    /** sha256sum format: {@code <hash>  <path>}, one file per line. */
    private void saveBaseline() {
        List<String> lines = new ArrayList<>();
        new TreeSet<>(baseline.keySet()).forEach(rel -> lines.add(baseline.get(rel) + "  " + rel));
        try {
            Files.write(baselineFile, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[bait] could not save the baseline " + baselineFile + ": " + e.getMessage());
        }
    }

    private static Map<String, String> lures() {
        Map<String, String> lures = new LinkedHashMap<>();
        lures.put("passwords.csv", """
                system,username,password,notes
                router-admin,admin,Rg9000!admin,change after audit
                nas,backup,Nas#2026backup,
                vpn,j.rahman,Spring2026!,temp - reset Monday
                """);
        lures.put("vpn.key", """
                -----BEGIN OPENVPN STATIC KEY V1-----
                6f1c2a9e4b7d03f58a2e91c6d4b07f3e
                a83d5c1f9e2b46d07c5a3e8f1b9d2c64
                -----END OPENVPN STATIC KEY V1-----
                """);
        // The honeytoken the Console plants at this location (ConsoleMain, "config/backup.cfg"):
        // replaying it against a decoy is how an intruder gives themself away.
        lures.put("config/backup.cfg", """
                # nightly backup job
                target=//nas01/backups
                schedule=02:00
                credentials=svc_backup:S3cr3tBackup!
                """);
        return Map.copyOf(lures);
    }
}
