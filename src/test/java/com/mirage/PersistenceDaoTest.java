package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.Alert;
import com.mirage.common.AttackEvent;
import com.mirage.common.Classification;
import com.mirage.common.LedgerEntry;
import com.mirage.common.Severity;
import com.mirage.console.db.AlertDao;
import com.mirage.console.db.Database;
import com.mirage.console.db.EventDao;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceDaoTest {

    @Test
    void appliesSchemaAndPersistsEventsAndAlerts() throws Exception {
        try (Database db = new Database("jdbc:h2:mem:mirage_test;DB_CLOSE_DELAY=-1")) {
            EventDao events = new EventDao(db);
            AlertDao alerts = new AlertDao(db);

            events.save(new AttackEvent("10.10.0.5", 4444, "http-login",
                    Action.LOGIN_ATTEMPT, "admin:hunter2", Instant.now()));
            events.save(new AttackEvent("10.10.0.5", 4444, "ftp",
                    Action.CONNECT, "", Instant.now()));
            assertEquals(2, events.count());

            Alert alert = new Alert("10.10.0.5",
                    new Classification("Credential brute-force", "T1110", Severity.HIGH), Instant.now());
            alerts.save(new LedgerEntry("0".repeat(64), "abc123", alert));
            assertEquals(1, alerts.count());
        }
    }
}
