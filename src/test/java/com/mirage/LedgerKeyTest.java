package com.mirage;

import com.mirage.console.KeyPairSigner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A-06: the ledger's signing key was new on every Console start and never stored, so the
 * signatures written by an earlier run could never be checked again. Agreed: the key is saved
 * in the data folder, locked with MIRAGE_LEDGER_PASSWORD when that is set.
 */
class LedgerKeyTest {

    @TempDir
    Path dir;

    private String keyFile() throws Exception {
        return Files.readString(dir.resolve(KeyPairSigner.KEY_FILE));
    }

    @Test
    void theKeyIsSavedAndTheSameKeyComesBackAfterARestart() throws Exception {
        KeyPairSigner first = KeyPairSigner.loadOrCreate(dir, null);
        KeyPairSigner second = KeyPairSigner.loadOrCreate(dir, null);
        assertEquals(first.keyId(), second.keyId());
        assertTrue(second.verify("entry-hash", first.sign("entry-hash")),
                "a signature from the earlier run verifies after the restart");
        assertTrue(Files.isRegularFile(dir.resolve(KeyPairSigner.PUBLIC_KEY_FILE)),
                "the public key is published next to it, so anyone can check the ledger");
        assertTrue(keyFile().contains("-----BEGIN PRIVATE KEY-----"));
    }

    @Test
    void withAPasswordTheSavedKeyIsLockedAndOnlyThatPasswordOpensIt() throws Exception {
        KeyPairSigner first = KeyPairSigner.loadOrCreate(dir, "lab-ledger-pw");
        assertTrue(keyFile().contains("-----BEGIN ENCRYPTED PRIVATE KEY-----"));
        assertFalse(keyFile().contains("-----BEGIN PRIVATE KEY-----"));
        assertEquals(first.keyId(), KeyPairSigner.loadOrCreate(dir, "lab-ledger-pw").keyId());

        IllegalArgumentException none = assertThrows(IllegalArgumentException.class,
                () -> KeyPairSigner.loadOrCreate(dir, null));
        assertTrue(none.getMessage().contains(KeyPairSigner.PASSWORD_ENV), none.getMessage());
        assertThrows(IllegalArgumentException.class, () -> KeyPairSigner.loadOrCreate(dir, "wrong"),
                "a wrong password must not quietly start a new key (that would break the chain)");
    }

    @Test
    void settingAPasswordLaterLocksTheExistingKey() throws Exception {
        String id = KeyPairSigner.loadOrCreate(dir, null).keyId();
        assertEquals(id, KeyPairSigner.loadOrCreate(dir, "added-later").keyId(), "same key, now locked");
        assertTrue(keyFile().contains("-----BEGIN ENCRYPTED PRIVATE KEY-----"));
    }
}
