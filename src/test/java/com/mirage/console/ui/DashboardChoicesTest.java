package com.mirage.console.ui;

import com.mirage.common.Classification;
import com.mirage.common.Severity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** B-33: the dashboard's counter target and its popup budget. */
class DashboardChoicesTest {

    private static Classification c(String technique, Severity severity) {
        return new Classification("x", technique, severity);
    }

    @Test
    void theCounterMirrorsTheMostSevereFindingNotTheLatest() {
        Classification replay = c("T1078", Severity.CRITICAL);
        assertEquals(replay, Dashboard.counterPick(replay, c("T1046", Severity.MEDIUM)),
                "a later port scan must not turn a replay counter into a scan");
        assertEquals("T1110.003", Dashboard.counterPick(c("T1110", Severity.HIGH), c("T1110.003", Severity.HIGH))
                .attackTechnique(), "at equal severity the latest wins");
        assertEquals("T1110", Dashboard.counterPick(null, c("T1110", Severity.HIGH)).attackTechnique());
    }

    @Test
    void popupsBeyondTheBudgetCloseTheLeastRecentlyUsed() {
        List<String> closed = new ArrayList<>();
        Map<String, String> popups = Dashboard.boundedPopups(3, closed::add);
        popups.put("a", "popup-a");
        popups.put("b", "popup-b");
        popups.put("c", "popup-c");
        popups.get("a"); // a was escalated again: most recent now
        popups.put("d", "popup-d");
        assertEquals(List.of("popup-b"), closed);
        assertEquals(3, popups.size());
        assertTrue(popups.containsKey("a") && popups.containsKey("d"));
    }
}
