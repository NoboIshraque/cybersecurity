package com.mirage.console;

import com.mirage.common.Classification;
import com.mirage.specter.Campaign;

import java.util.Optional;

/**
 * Strategy: map a detected attacker technique to the Specter campaign that turns
 * that same method back on the source. Choosing the campaign is pure; running it
 * (and the ScopeGuard check) is {@link CounterOffensive}'s job.
 */
public interface CounterStrategy {

    Optional<Campaign> counterFor(Classification technique);
}
