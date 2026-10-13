package com.mirage.console;

/**
 * Signs and verifies ledger entries. {@link #NONE} is a no-op (no signatures),
 * which keeps the ledger working unsigned for tests and simple runs.
 */
public interface LedgerSigner {

    String sign(String data);

    boolean verify(String data, String signature);

    LedgerSigner NONE = new LedgerSigner() {
        @Override public String sign(String data) { return ""; }
        @Override public boolean verify(String data, String signature) { return true; }
    };
}
