package com.mirage.console.detection;

import com.mirage.common.Action;
import com.mirage.common.AttackerProfile;
import com.mirage.common.Classification;
import com.mirage.common.Severity;

import java.util.Optional;

/**
 * Fires when a decoy reports that a trap credential worked and the intruder is
 * now "inside" a honeypot session ({@link Action#DECOY_ACCESS}). Because a real
 * user never has these credentials and nothing of value is really exposed, this
 * is near-certain, highest-severity evidence of a hands-on intrusion.
 */
public final class DecoyAccessRule implements DetectionRule {

    @Override
    public Optional<Classification> classify(AttackerProfile profile) {
        boolean inside = profile.count(Action.DECOY_ACCESS) > 0;
        return inside
                ? Optional.of(new Classification(
                        "Intruder inside decoy (honeypot session)", "T1078", Severity.CRITICAL))
                : Optional.empty();
    }
}
