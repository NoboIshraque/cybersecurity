package com.mirage.console;

import com.mirage.common.AttackEvent;
import com.mirage.common.LedgerEntry;

/** Where the engine records events and alerts. {@link #NONE} is an in-memory no-op. */
public interface Persistence {

    void saveEvent(AttackEvent event);

    void saveAlert(LedgerEntry entry);

    Persistence NONE = new Persistence() {
        @Override public void saveEvent(AttackEvent event) { }
        @Override public void saveAlert(LedgerEntry entry) { }
    };
}
