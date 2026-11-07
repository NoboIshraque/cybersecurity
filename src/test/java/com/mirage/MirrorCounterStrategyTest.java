package com.mirage;

import com.mirage.common.Classification;
import com.mirage.common.Severity;
import com.mirage.console.CounterStrategy;
import com.mirage.console.MirrorCounterStrategy;
import com.mirage.specter.Campaign;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MirrorCounterStrategyTest {

    private final CounterStrategy strategy = new MirrorCounterStrategy();

    private static Classification tech(String code) {
        return new Classification("detected", code, Severity.HIGH);
    }

    private String campaignFor(String code) {
        Optional<Campaign> campaign = strategy.counterFor(tech(code));
        assertTrue(campaign.isPresent());
        return campaign.get().name();
    }

    @Test
    void mirrorsEachKnownTechniqueBackAtTheSource() {
        assertEquals("mirror-scan", campaignFor("T1046"));
        assertEquals("mirror-brute", campaignFor("T1110"));
        assertEquals("mirror-spray", campaignFor("T1110.003"));
        assertEquals("mirror-replay", campaignFor("T1078"));
    }

    @Test
    void fallsBackToAProbeWhenThereIsNoLikeForLikeReply() {
        assertEquals("counter-probe", campaignFor("T1021")); // lateral movement
        assertEquals("counter-probe", campaignFor("T1485")); // ransomware / bait
        assertEquals("counter-probe", campaignFor("T9999")); // unknown
    }

    @Test
    void fullCounterBundlesEveryMethodForASimultaneousStrike() {
        Campaign full = strategy.fullCounter();
        assertEquals("full-counter", full.name());
        assertEquals(4, full.strategies().size());
    }
}
