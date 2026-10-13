package com.mirage.specter;

import java.util.List;

/** A named bundle of attack strategies run as one scenario. */
public final class Campaign {

    private final String name;
    private final List<AttackStrategy> strategies;

    public Campaign(String name, List<AttackStrategy> strategies) {
        this.name = name;
        this.strategies = List.copyOf(strategies);
    }

    public String name() {
        return name;
    }

    public List<AttackStrategy> strategies() {
        return strategies;
    }
}
