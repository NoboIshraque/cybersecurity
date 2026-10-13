package com.mirage.common;

import java.time.Instant;

/** A classified event, ready to be recorded in the hash-chained ledger. */
public record Alert(String sourceIp, Classification classification, Instant timestamp) {

    /** Stable string form used as the hash input for the ledger. */
    public String canonical() {
        return sourceIp + "|"
             + classification.label() + "|"
             + classification.attackTechnique() + "|"
             + classification.severity() + "|"
             + timestamp;
    }
}
