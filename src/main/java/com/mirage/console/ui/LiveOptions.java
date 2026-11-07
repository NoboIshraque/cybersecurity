package com.mirage.console.ui;

import com.mirage.common.trace.TraceBus;
import com.mirage.console.AutoResponse;

/**
 * The live-demo wiring the dashboard needs on top of its data sources. With
 * {@link #none()} the dashboard behaves as it always has: no threat popups, no
 * operation shells, no auto-response switch.
 *
 * @param trace          the Console's trace bus (null = no shells or popups)
 * @param autoResponse   the shared auto-response switch (null = no checkbox)
 * @param consoleAddress "host:port" the Sensors / attacker laptop should use, for the status bar (may be null)
 */
public record LiveOptions(TraceBus trace, AutoResponse autoResponse, String consoleAddress) {

    public static LiveOptions none() {
        return new LiveOptions(null, null, null);
    }
}
