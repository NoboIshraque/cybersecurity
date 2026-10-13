package com.mirage.common;

/** One link in the tamper-evident alert ledger. */
public record LedgerEntry(String prevHash, String hash, Alert alert) {
}
