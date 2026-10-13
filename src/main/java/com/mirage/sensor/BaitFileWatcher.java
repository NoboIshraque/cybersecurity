package com.mirage.sensor;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * Watches a directory of bait files. Any create/modify/delete emits a
 * FILE_CHANGE event — early warning of tampering or ransomware. Bait-file
 * events have no network source, so they use the sentinel "local-filesystem".
 */
public final class BaitFileWatcher {

    private final Path directory;
    private final Consumer<AttackEvent> sink;
    private volatile boolean running;

    public BaitFileWatcher(Path directory, Consumer<AttackEvent> sink) {
        this.directory = directory;
        this.sink = sink;
    }

    public void start() throws IOException {
        WatchService watcher = directory.getFileSystem().newWatchService();
        directory.register(watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        running = true;
        Thread t = new Thread(() -> loop(watcher), "bait-watcher");
        t.setDaemon(true);
        t.start();
    }

    private void loop(WatchService watcher) {
        while (running) {
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            for (WatchEvent<?> watchEvent : key.pollEvents()) {
                if (watchEvent.kind() == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                String file = String.valueOf(watchEvent.context());
                sink.accept(new AttackEvent("local-filesystem", 0, "bait-file",
                        Action.FILE_CHANGE, watchEvent.kind().name() + " " + file, Instant.now()));
            }
            key.reset();
        }
    }

    public void stop() {
        running = false;
    }
}
