package com.mirage.console.ui;

import com.mirage.common.CommandType;

/** An operator action from the dashboard or a threat popup. Every one is ScopeGuard-checked. */
public enum Operation {
    TARPIT("Tarpit", CommandType.TARPIT),
    FEED_FAKE("Feed fake data", CommandType.FEED_FAKE),
    BLOCK("Block", CommandType.BLOCK),
    UNBLOCK("Unblock", CommandType.UNBLOCK),
    COUNTER("Counter-attack", null),
    COUNTER_ALL("Simultaneous counter", null);

    private final String label;
    private final CommandType command;

    Operation(String label, CommandType command) {
        this.label = label;
        this.command = command;
    }

    public String label() {
        return label;
    }

    /** The Sensor command this sends, or {@code null} for a counter. */
    public CommandType command() {
        return command;
    }

    public boolean isCounter() {
        return command == null;
    }
}
