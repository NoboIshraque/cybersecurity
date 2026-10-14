package com.mirage.console;

import com.mirage.common.AttackerProfile;
import com.mirage.common.AttackerSnapshot;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe, read-only-for-the-UI view of attacker activity. The correlation
 * engine (the single mutator of profiles) publishes an immutable
 * {@link AttackerSnapshot} per source here; the Swing dashboard reads snapshots on
 * the event thread without touching the live profiles, so no mutable state is ever
 * shared across threads.
 */
public final class AttackerDirectory {

    private final Map<String, AttackerSnapshot> snapshots = new ConcurrentHashMap<>();

    /** Called on the engine thread whenever a source's profile changes. */
    public void update(AttackerProfile profile) {
        snapshots.put(profile.sourceIp(), new AttackerSnapshot(profile.sourceIp(), profile.events()));
    }

    /** The latest snapshot for a source, or {@code null} if none has been seen. */
    public AttackerSnapshot snapshot(String sourceIp) {
        return snapshots.get(sourceIp);
    }

    /** Every source seen so far (attackers that have touched a decoy). */
    public List<String> sources() {
        return List.copyOf(snapshots.keySet());
    }
}
