package com.mirage.console.detection;

import com.mirage.common.*;

import java.util.Optional;

public final class PortScanRule implements DetectionRule {

    private static final int DISTINCT_SERVICES = 3;

    @Override
    public Optional<Classification> classify(AttackerProfile profile) {
        // distinct decoy types this source CONNECTed to or PORT_PROBEd, kept by the profile (B-02)
        if (profile.probedServices().size() >= DISTINCT_SERVICES) {
            return Optional.of(new Classification(
                    "Port / service scan", "T1046", Severity.MEDIUM));
        }
        return Optional.empty();
    }
}
