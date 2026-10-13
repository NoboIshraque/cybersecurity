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

    public synchronized LedgerEntry append(Alert alert) {
        String hash = sha256(lastHash + alert.canonical());
        String signature = signer.sign(hash);
        LedgerEntry entry = new LedgerEntry(lastHash, hash, alert, signature);
        chain.add(entry);
        lastHash = hash;
        return entry;
    }

    public synchronized boolean verify() {
        return verifyChain(new ArrayList<>(chain), signer);
    }

    /** Verify the hash chain only (no signatures). */
    public static boolean verifyChain(List<LedgerEntry> chain) {
        return verifyChain(chain, LedgerSigner.NONE);
    }

    /** Verify the hash chain and every entry's signature. */
    public static boolean verifyChain(List<LedgerEntry> chain, LedgerSigner signer) {
        String prev = GENESIS;
        for (LedgerEntry e : chain) {
            if (!e.prevHash().equals(prev)) {
                return false;
            }
            if (!e.hash().equals(sha256(prev + e.alert().canonical()))) {
                return false;
            }
            if (!signer.verify(e.hash(), e.signature())) {
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
