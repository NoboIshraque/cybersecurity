package com.mirage.console.detection;

import com.mirage.common.Action;
import com.mirage.common.AttackerProfile;
import com.mirage.common.Classification;
import com.mirage.common.Severity;
import com.mirage.console.HoneytokenStore;

import java.util.Optional;

/**
 * Fires when a captured login attempt uses credentials that match a planted
 * honeytoken, or on an explicit HONEYTOKEN_USE event. Either is near-certain
 * evidence that a source has stolen credentials it should never have had.
 */
public final class HoneytokenUseRule implements DetectionRule {

    private final HoneytokenStore store;

    public HoneytokenUseRule(HoneytokenStore store) {
        this.store = store;
    }

    @Override
    public Optional<Classification> classify(AttackerProfile profile) {
        // One lookup per planted token in the source's set of tried credentials (B-02).
        boolean used = profile.count(Action.HONEYTOKEN_USE) > 0
                || store.all().stream().anyMatch(t -> profile.triedCredentials(t.credentials()));
        return used
                ? Optional.of(new Classification(
                        "Stolen credentials used (honeytoken)", "T1078", Severity.CRITICAL))
                : Optional.empty();
    }
}
