package com.mirage.console;

import com.mirage.common.Classification;
import com.mirage.common.ScopeGuard;
import com.mirage.console.response.BlockSourceAction;
import com.mirage.console.response.FeedFakeDataAction;
import com.mirage.console.response.ResponseAction;
import com.mirage.console.response.TarpitAction;

import java.util.List;

/**
 * Strategy: map a classification to the active-defence actions to run. Every
 * target is checked by {@link ScopeGuard} first, so nothing outside the lab
 * range can ever be acted on.
 */
public final class ResponsePolicy {

    private final ScopeGuard guard;

    public ResponsePolicy(ScopeGuard guard) {
        this.guard = guard;
    }

    public List<ResponseAction> actionsFor(String sourceIp, Classification classification) {
        guard.require(sourceIp); // safety boundary
        return switch (classification.severity()) {
            case LOW, MEDIUM -> List.of(new TarpitAction(sourceIp));
            case HIGH        -> List.of(new TarpitAction(sourceIp), new FeedFakeDataAction(sourceIp));
            case CRITICAL    -> List.of(new BlockSourceAction(sourceIp));
        };
    }
}
