package com.mirage.console.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FB-10: the op shell's closing banner is written once the command has been sent, and the
 * Sensor's ACK arrives on another thread, so it can come first. "BLOCK DISPATCHED — AWAITING
 * SENSOR ACK" then appeared under "sensor-… confirms: BLOCK … applied" (it did in Figure 11).
 * The banner now says only what is true in either order; the ACK line is the confirmation.
 */
class DispatchBannerTest {

    private static final String SENT = "2026-11-08T10:00:00Z  BLOCK  →  127.0.0.1";

    @Test
    void aSentCommandsBannerIsTrueWhicheverComesFirstItOrTheAck() {
        for (Operation op : new Operation[]{Operation.TARPIT, Operation.FEED_FAKE, Operation.BLOCK, Operation.UNBLOCK}) {
            String text = OperationShells.bannerText(op, SENT);
            assertEquals(op.label().toUpperCase(java.util.Locale.ROOT) + " DISPATCHED", text);
            assertFalse(text.contains("AWAITING"), text);
        }
    }

    @Test
    void theOtherBannersAreUnchanged() {
        assertEquals("REFUSED — OUTSIDE LAB RANGE", OperationShells.bannerText(Operation.BLOCK,
                "2026-11-08T10:00:00Z  REFUSED BLOCK  →  8.8.8.8  (outside the lab range)"));
        assertEquals("BLOCK — NOT SENT", OperationShells.bannerText(Operation.BLOCK, "failed: boom"));
        assertEquals("COUNTER LAUNCHED", OperationShells.bannerText(Operation.COUNTER, "counter started"));
        assertEquals("COUNTERS LAUNCHED (CONCURRENT)",
                OperationShells.bannerText(Operation.COUNTER_ALL, "counters started"));
    }
}
