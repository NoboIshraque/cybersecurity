package com.mirage.console.detection;

import com.mirage.common.Action;
import com.mirage.common.AttackerProfile;
import com.mirage.common.Classification;
import com.mirage.common.Severity;

import java.util.Optional;

/** Any change to a bait file is treated as critical (tampering or ransomware). */
public final class BaitFileRule implements DetectionRule {

    @Override
    public Optional<Classification> classify(AttackerProfile profile) {
        boolean tampered = profile.count(Action.FILE_CHANGE) > 0;
        return tampered
                ? Optional.of(new Classification(
                        "Bait file tampered (possible ransomware)", "T1485", Severity.CRITICAL))
                : Optional.empty();
    }
}
