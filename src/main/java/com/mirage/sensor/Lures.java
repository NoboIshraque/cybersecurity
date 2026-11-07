package com.mirage.sensor;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * The lure files: canned, worthless content that looks valuable. {@link BaitFileWatcher} plants
 * them in the bait folder, and the decoys show the same files to a source the Console has told
 * them to feed fake data (B-09), so the two stories match. Nothing real is in them; the one
 * live value is the backup.cfg honeytoken, which gives away whoever later replays it.
 */
final class Lures {

    /** Path relative to the bait folder → content, sorted by path. */
    static final Map<String, String> FILES = files();

    private Lures() {
    }

    private static Map<String, String> files() {
        Map<String, String> lures = new TreeMap<>();
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
        return Collections.unmodifiableMap(lures);
    }
}
