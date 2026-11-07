package com.mirage.console.detection;

import com.mirage.common.*;

import java.util.Optional;

public final class PortScanRule implements DetectionRule {

    /** B-28: 2, not 3: only three decoys exist, so 3 would miss a scan whenever one is down. */
    private static final int DISTINCT_SERVICES = 2;

    @Override
    public Optional<Classification> classify(AttackerProfile profile) {
        // distinct decoy types this source CONNECTed to or PORT_PROBEd within a minute (B-02, B-28)
        if (profile.recentServices() >= DISTINCT_SERVICES) {
            return Optional.of(new Classification(
                    "Port / service scan", "T1046", Severity.MEDIUM));
        }
        return Optional.empty();
    }
}
