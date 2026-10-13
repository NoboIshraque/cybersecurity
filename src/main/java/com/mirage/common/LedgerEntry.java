package com.mirage.common;

/** One link in the tamper-evident alert ledger, with an optional signature over its hash. */
public record LedgerEntry(String prevHash, String hash, Alert alert, String signature) {

    public LedgerEntry(String prevHash, String hash, Alert alert) {
        this(prevHash, hash, alert, "");
    }
}
