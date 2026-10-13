package com.mirage.specter;

/** Strategy: one simulated attack behaviour, run against a lab target. */
public interface AttackStrategy {
    void run(Target target);
    String name();
}
