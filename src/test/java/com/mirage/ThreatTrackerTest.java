package com.mirage;

import com.mirage.common.Alert;
import com.mirage.common.Classification;
import com.mirage.common.Severity;
import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;
import com.mirage.console.ThreatLevel;
import com.mirage.console.ThreatTracker;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** The threat popup's escalation rules, without any Swing. */
class ThreatTrackerTest {

    private static final String IP = "127.0.0.9";

    private static Alert alert(Severity severity) {
        return new Alert(IP, new Classification("test", "T0000", severity), Instant.now());
    }

    private static TraceEvent ack(String command, String ip) {
        return new TraceEvent(Instant.now(), "console", "rx-sensor-1", "ack", ip, TraceLevel.GOOD,
                "sensor-1 confirms: " + command + " " + ip + " applied");
    }

    @Test
    void escalatesOnlyUpward() {
        ThreatTracker t = new ThreatTracker();
        assertEquals(Optional.of(ThreatLevel.PROBE_DETECTED), t.onAlert(alert(Severity.MEDIUM)));
        assertEquals(Optional.of(ThreatLevel.UNDER_ATTACK), t.onAlert(alert(Severity.HIGH)));
        assertEquals(Optional.empty(), t.onAlert(alert(Severity.LOW)), "a lesser alert never lowers the level");
        assertEquals(Optional.of(ThreatLevel.COMPROMISED), t.onAlert(alert(Severity.CRITICAL)));
        assertEquals(Optional.empty(), t.onAlert(alert(Severity.CRITICAL)), "same level is not a change");
        assertEquals(ThreatLevel.COMPROMISED, t.levelOf(IP));
    }

    @Test
    void blockAckNeutralisesAndUnblockRestores() {
        ThreatTracker t = new ThreatTracker();
        t.onAlert(alert(Severity.HIGH));
        assertEquals(Optional.empty(), t.onTrace(ack("TARPIT", IP)), "a tarpit does not neutralise");
        assertEquals(Optional.of(ThreatLevel.NEUTRALISED), t.onTrace(ack("BLOCK", IP)));
        assertEquals(Optional.empty(), t.onTrace(ack("BLOCK", IP)), "a second sensor's ACK is not a change");
        assertEquals(Optional.empty(), t.onAlert(alert(Severity.CRITICAL)), "stays neutralised while blocked");
        assertEquals(ThreatLevel.NEUTRALISED, t.levelOf(IP));
        assertEquals(Optional.of(ThreatLevel.COMPROMISED), t.onTrace(ack("UNBLOCK", IP)),
                "unblock restores the worst level seen, including attempts made while blocked");
    }

    @Test
    void ignoresUnknownSourcesAndOtherLines() {
        ThreatTracker t = new ThreatTracker();
        assertEquals(Optional.empty(), t.onTrace(ack("BLOCK", IP)), "no alert yet for this source");
        t.onAlert(alert(Severity.CRITICAL));
        TraceEvent notAck = new TraceEvent(Instant.now(), "console", "x", "dispatch", IP, TraceLevel.INFO,
                "broadcast BLOCK " + IP + " to 1 sensor(s)");
        assertEquals(Optional.empty(), t.onTrace(notAck), "a dispatch is not a confirmation");
        assertEquals(Optional.empty(), t.onTrace(ack("UNBLOCK", IP)), "UNBLOCK is not mistaken for BLOCK");
        assertEquals(ThreatLevel.COMPROMISED, t.levelOf(IP));
    }
}
