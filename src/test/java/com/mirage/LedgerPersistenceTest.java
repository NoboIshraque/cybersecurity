package com.mirage;

import com.mirage.common.Alert;
import com.mirage.common.Classification;
import com.mirage.common.LedgerEntry;
import com.mirage.common.Severity;
import com.mirage.console.AlertLedger;
import com.mirage.console.H2Persistence;
import com.mirage.console.KeyPairSigner;
import com.mirage.console.db.AlertDao;
import com.mirage.console.db.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-30 / A-06: every Console start began a new chain at the genesis hash in the same table, and
 * nothing stored was ever loaded or checked. Now the stored chain is loaded and verified at
 * start-up, new alerts continue it, and an edit made to the database in between is caught.
 */
class LedgerPersistenceTest {

    @TempDir
    Path dir;

    private String url() {
        return "jdbc:h2:file:" + dir.resolve("mirage").toAbsolutePath();
    }

    /** Nanosecond timestamps: the reloaded alert must hash exactly as it did when written. */
    private static Alert alert(int n, String technique, Severity severity) {
        return new Alert("10.10.0." + n, new Classification("finding " + n, technique, severity),
                Instant.parse("2026-11-07T10:00:00.123456789Z").plusSeconds(n));
    }

    /** One Console run: load the stored chain, append, save. Returns the ledger after the run. */
    private AlertLedger run(KeyPairSigner signer, Alert... alerts) throws Exception {
        try (Database db = new Database(url())) {
            H2Persistence persistence = new H2Persistence(db, signer.keyId());
            AlertLedger ledger = new AlertLedger(signer, new AlertDao(db).loadChain());
            for (Alert a : alerts) {
                persistence.saveAlert(ledger.append(a));
            }
            return ledger;
        }
    }

    private List<LedgerEntry> stored() throws Exception {
        try (Database db = new Database(url())) {
            return new AlertDao(db).loadChain();
        }
    }

    @Test
    void theChainContinuesAcrossARestartAndStillVerifies() throws Exception {
        KeyPairSigner signer = KeyPairSigner.loadOrCreate(dir, null);
        AlertLedger first = run(signer, alert(1, "T1046", Severity.MEDIUM), alert(2, "T1110", Severity.HIGH));
        String lastHashOfFirstRun = first.entries().get(1).hash();

        KeyPairSigner afterRestart = KeyPairSigner.loadOrCreate(dir, null);
        AlertLedger second = run(afterRestart, alert(3, "T1078", Severity.CRITICAL));
        assertEquals(3, second.size(), "the first run's alerts were loaded");
        assertEquals(lastHashOfFirstRun, second.entries().get(2).prevHash(),
                "the new alert links to the last stored one, not to the genesis hash");

        List<LedgerEntry> all = stored();
        assertEquals(3, all.size());
        assertTrue(AlertLedger.verifyChain(all, afterRestart), "one chain, every signature checkable");
    }

    @Test
    void anEditMadeToTheDatabaseBetweenRunsIsCaughtAtTheNextStart() throws Exception {
        KeyPairSigner signer = KeyPairSigner.loadOrCreate(dir, null);
        run(signer, alert(1, "T1046", Severity.MEDIUM), alert(2, "T1110", Severity.HIGH));
        try (Connection c = DriverManager.getConnection(url()); Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE alert SET severity = 'LOW' WHERE id = (SELECT MIN(id) FROM alert)");
        }
        AlertLedger reloaded = run(signer);
        assertEquals(0, reloaded.firstBrokenIndex(), "the edited first entry is flagged");
    }

    @Test
    void rowsFromBeforeKeysWereStoredAreSetAsideNotMixedIn() throws Exception {
        try (Database db = new Database(url())) {
            new AlertDao(db).save(new LedgerEntry("0".repeat(64), "f".repeat(64),
                    alert(9, "T1110", Severity.HIGH), "sig-from-a-lost-key"));
        }
        KeyPairSigner signer = KeyPairSigner.loadOrCreate(dir, null);
        AlertLedger ledger = run(signer, alert(1, "T1046", Severity.MEDIUM));
        assertEquals(1, ledger.size());
        assertTrue(ledger.verify(), "a legacy row from a lost key must not break the new chain");
        try (Database db = new Database(url())) {
            assertEquals(1, new AlertDao(db).legacyCount());
        }
    }
}
