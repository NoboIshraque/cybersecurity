package com.mirage.console;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A shared, thread-safe on/off switch for automatic active defence. When off, the
 * correlation engine still records and surfaces every alert, but does not dispatch
 * the automatic {@link ResponsePolicy} response — the operator responds by hand
 * from the dashboard. The demo runs with this OFF so the audience watches the
 * operator act; flipping it ON restores the fully automatic behaviour.
 */
public final class AutoResponse {

    private final AtomicBoolean enabled;

    public AutoResponse(boolean enabled) {
        this.enabled = new AtomicBoolean(enabled);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }
}
