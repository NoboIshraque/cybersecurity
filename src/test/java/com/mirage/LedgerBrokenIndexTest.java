package com.mirage;

import com.mirage.common.Alert;
import com.mirage.common.Classification;
import com.mirage.common.LedgerEntry;
import com.mirage.common.Severity;
import com.mirage.console.AlertLedger;
import com.mirage.console.LedgerSigner;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LedgerBrokenIndexTest {

    private static Alert alert(String ip, Severity sev) {
        return new Alert(ip, new Classification("rule", "T0000", sev), Instant.parse("2026-10-14T00:00:00Z"));
    }

    @Test
    void intactChainHasNoBrokenIndex() {
        AlertLedger ledger = new AlertLedger();
        ledger.append(alert("10.10.0.5", Severity.MEDIUM));
        ledger.append(alert("10.10.0.6", Severity.HIGH));
        ledger.append(alert("10.10.0.7", Severity.CRITICAL));

        assertTrue(ledger.verify());
        assertEquals(-1, ledger.firstBrokenIndex());
    }

    @Test
    void reportsTheIndexOfAnAlteredEntry() {
        AlertLedger ledger = new AlertLedger();
        ledger.append(alert("10.10.0.5", Severity.MEDIUM));
        ledger.append(alert("10.10.0.6", Severity.HIGH));
        ledger.append(alert("10.10.0.7", Severity.CRITICAL));

        List<LedgerEntry> chain = new ArrayList<>(ledger.entries());
        LedgerEntry original = chain.get(1);
        // keep the recorded hashes but swap the alert -> the recomputed hash no longer matches
        chain.set(1, new LedgerEntry(original.prevHash(), original.hash(),
                alert("9.9.9.9", Severity.LOW), original.signature()));

        assertEquals(1, AlertLedger.firstBrokenIndex(chain, LedgerSigner.NONE));
        assertFalse(AlertLedger.verifyChain(chain));
    }
}
