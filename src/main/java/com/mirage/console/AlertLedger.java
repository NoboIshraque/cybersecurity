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
 * previous hash; a {@link LedgerSigner} additionally signs each hash. Any later
 * edit breaks the chain (and the signature), and {@link #verify()} catches it.
 */
public final class AlertLedger {

    private static final String GENESIS = "0".repeat(64);

    private final List<LedgerEntry> chain = new ArrayList<>();
    private final LedgerSigner signer;
    private String lastHash = GENESIS;

    public AlertLedger() {
        this(LedgerSigner.NONE);
    }

    public AlertLedger(LedgerSigner signer) {
        this.signer = signer;
    }

    /**
     * Continue a stored chain (B-30): {@code stored} is loaded as it is, and the next entry links
     * to its last hash. {@link #verify()} and {@link #firstBrokenIndex()} cover the stored entries
     * too, so an edit made to the database between runs shows up at start-up.
     */
    public AlertLedger(LedgerSigner signer, List<LedgerEntry> stored) {
        this(signer);
        chain.addAll(stored);
        if (!stored.isEmpty()) {
            lastHash = stored.get(stored.size() - 1).hash();
        }
    }

    public synchronized LedgerEntry append(Alert alert) {
        String hash = sha256(lastHash + alert.canonical());
        String signature = signer.sign(hash);
        LedgerEntry entry = new LedgerEntry(lastHash, hash, alert, signature);
        chain.add(entry);
        lastHash = hash;
        return entry;
    }

    public synchronized boolean verify() {
        return firstBrokenIndex() < 0;
    }

    /**
     * The 0-based position of the first entry that fails verification, or -1 if the
     * whole chain is intact. The dashboard uses this to highlight the broken link.
     */
    public synchronized int firstBrokenIndex() {
        return firstBrokenIndex(new ArrayList<>(chain), signer);
    }

    /** Verify the hash chain only (no signatures). */
    public static boolean verifyChain(List<LedgerEntry> chain) {
        return verifyChain(chain, LedgerSigner.NONE);
    }

    /** Verify the hash chain and every entry's signature. */
    public static boolean verifyChain(List<LedgerEntry> chain, LedgerSigner signer) {
        return firstBrokenIndex(chain, signer) < 0;
    }

    /** The index of the first entry whose link or signature is broken, or -1 if intact. */
    public static int firstBrokenIndex(List<LedgerEntry> chain, LedgerSigner signer) {
        String prev = GENESIS;
        for (int i = 0; i < chain.size(); i++) {
            LedgerEntry e = chain.get(i);
            if (!e.prevHash().equals(prev)
                    || !e.hash().equals(sha256(prev + e.alert().canonical()))
                    || !signer.verify(e.hash(), e.signature())) {
                return i;
            }
            prev = e.hash();
        }
        return -1;
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
