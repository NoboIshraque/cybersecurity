package com.mirage.console;

import com.mirage.common.Severity;

/**
 * How bad things are for one attacker source, as the Console's threat popup shows
 * it. Levels only climb while the source is active; a confirmed BLOCK from a
 * Sensor marks it {@link #NEUTRALISED}.
 */
public enum ThreatLevel {
    PROBE_DETECTED("PROBE DETECTED"),
    UNDER_ATTACK("UNDER ATTACK"),
    COMPROMISED("COMPROMISED"),
    NEUTRALISED("NEUTRALISED");

    private final String banner;

    ThreatLevel(String banner) {
        this.banner = banner;
    }

    /** The banner text drawn in the popup. */
    public String banner() {
        return banner;
    }

    /** The level an alert of this severity raises a source to. */
    public static ThreatLevel forSeverity(Severity severity) {
        return switch (severity) {
            case LOW, MEDIUM -> PROBE_DETECTED;
            case HIGH -> UNDER_ATTACK;
            case CRITICAL -> COMPROMISED;
        };
    }
}
