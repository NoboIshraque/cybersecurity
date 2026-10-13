package com.mirage.console;

import com.mirage.common.AttackEvent;
import com.mirage.common.LedgerEntry;
import com.mirage.console.db.AlertDao;
import com.mirage.console.db.Database;
import com.mirage.console.db.EventDao;

/** Persistence backed by the embedded H2 database. */
public final class H2Persistence implements Persistence {

    private final EventDao events;
    private final AlertDao alerts;

    public H2Persistence(Database db) {
        this.events = new EventDao(db);
        this.alerts = new AlertDao(db);
    }

    @Override
    public void saveEvent(AttackEvent event) {
        events.save(event);
    }

    @Override
    public void saveAlert(LedgerEntry entry) {
        alerts.save(entry);
    }
}
