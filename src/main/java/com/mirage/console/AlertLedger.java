package com.mirage.console;

import com.mirage.common.Alert;
import com.mirage.common.LedgerEntry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * Append-only, hash-chained ledger of alerts. Each entry's hash covers the
 * previous hash, so any later edit breaks the chain and {@link #verify()}
 * catches it.
 */
public final class AlertLedger {

    private static final String GENESIS = "0".repeat(64);

    private final List<LedgerEntry> chain = new ArrayList<>();
    private String lastHash = GENESIS;

    public synchronized LedgerEntry append(Alert alert) {
        String hash = sha256(lastHash + alert.canonical());
        LedgerEntry entry = new LedgerEntry(lastHash, hash, alert);
        chain.add(entry);
        lastHash = hash;
        return entry;
    }

    public synchronized boolean verify() {
        return verifyChain(new ArrayList<>(chain));
    }

    /** Walk any chain and confirm every link hashes to the next. */
    public static boolean verifyChain(List<LedgerEntry> chain) {
        String prev = GENESIS;
        for (LedgerEntry e : chain) {
            if (!e.prevHash().equals(prev)) {
                return false;
            }
            if (!e.hash().equals(sha256(prev + e.alert().canonical()))) {
                return false;
            }
            prev = e.hash();
        }
        return true;
    }

    public synchronized List<LedgerEntry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(chain));
    }

    public synchronized int size() {
        return chain.size();
    }

    static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
