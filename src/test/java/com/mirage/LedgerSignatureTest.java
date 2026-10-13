package com.mirage;

import com.mirage.common.Alert;
import com.mirage.common.Classification;
import com.mirage.common.LedgerEntry;
import com.mirage.common.Severity;
import com.mirage.console.AlertLedger;
import com.mirage.console.KeyPairSigner;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LedgerSignatureTest {

    private static Alert alert(String ip, Severity severity) {
        return new Alert(ip, new Classification("test", "T0000", severity), Instant.now());
    }

    @Test
    void signsAndVerifiesASignedChain() {
        KeyPairSigner signer = new KeyPairSigner();
        AlertLedger ledger = new AlertLedger(signer);
        ledger.append(alert("10.10.0.5", Severity.HIGH));
        ledger.append(alert("10.10.0.6", Severity.CRITICAL));

        assertFalse(ledger.entries().get(0).signature().isEmpty(), "entries should be signed");
        assertTrue(ledger.verify(), "a signed, intact chain should verify");
    }

    @Test
    void detectsAForgedEntry() {
        KeyPairSigner signer = new KeyPairSigner();
        AlertLedger ledger = new AlertLedger(signer);
        ledger.append(alert("10.10.0.5", Severity.HIGH));
        ledger.append(alert("10.10.0.6", Severity.LOW));

        List<LedgerEntry> tampered = new ArrayList<>(ledger.entries());
        LedgerEntry original = tampered.get(1);
        // keep the hashes but swap the alert and re-sign is impossible without the key
        tampered.set(1, new LedgerEntry(original.prevHash(), original.hash(),
                alert("9.9.9.9", Severity.CRITICAL), original.signature()));

        assertFalse(AlertLedger.verifyChain(tampered, signer));
    }

    @Test
    void signatureRoundTripRejectsWrongData() {
        KeyPairSigner signer = new KeyPairSigner();
        String sig = signer.sign("hash-A");
        assertTrue(signer.verify("hash-A", sig));
        assertFalse(signer.verify("hash-B", sig));
    }
}
