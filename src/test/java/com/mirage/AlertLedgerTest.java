package com.mirage;

import com.mirage.common.Alert;
import com.mirage.common.Classification;
import com.mirage.common.LedgerEntry;
import com.mirage.common.Severity;
import com.mirage.console.AlertLedger;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AlertLedgerTest {

    private static Alert alert(String ip, Severity severity) {
        return new Alert(ip, new Classification("test", "T0000", severity), Instant.now());
    }

    @Test
    void verifiesIntactChain() {
        AlertLedger ledger = new AlertLedger();
        ledger.append(alert("10.10.0.5", Severity.HIGH));
        ledger.append(alert("10.10.0.6", Severity.LOW));
        ledger.append(alert("10.10.0.7", Severity.CRITICAL));

        assertEquals(3, ledger.size());
        assertTrue(ledger.verify());
    }

    @Test
    void detectsTampering() {
        AlertLedger ledger = new AlertLedger();
        ledger.append(alert("10.10.0.5", Severity.HIGH));
        ledger.append(alert("10.10.0.6", Severity.LOW));

        List<LedgerEntry> tampered = new ArrayList<>(ledger.entries());
        LedgerEntry original = tampered.get(1);
        // Keep the stored hashes but swap the alert; the recomputed hash won't match.
        tampered.set(1, new LedgerEntry(original.prevHash(), original.hash(),
                alert("9.9.9.9", Severity.CRITICAL)));

        assertFalse(AlertLedger.verifyChain(tampered));
    }
}
