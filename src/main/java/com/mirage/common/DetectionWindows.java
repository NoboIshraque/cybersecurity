package com.mirage.common;

import java.time.Duration;

/**
 * The time windows the counting rules look at (B-28). Activity is counted only if it falls
 * within the window before the source's latest event, so a handful of attempts spread over a
 * week is not an attack. The windows are measured on the Sensor's event timestamps: a Sensor
 * whose clock is off shifts every timestamp by the same amount, so the gaps stay right (B-14).
 */
public final class DetectionWindows {

    /** Brute force: different passwords against one account. */
    public static final Duration BRUTE_FORCE = Duration.ofSeconds(60);
    /** Password spray: one password against different accounts (sprays are slow by design). */
    public static final Duration PASSWORD_SPRAY = Duration.ofMinutes(10);
    /** Port / service scan: different decoy services touched. */
    public static final Duration PORT_SCAN = Duration.ofSeconds(60);

    private DetectionWindows() {
    }
}
