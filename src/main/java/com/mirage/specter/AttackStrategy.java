package com.mirage.specter;

/** Strategy: one simulated attack behaviour, run against a lab target. */
public interface AttackStrategy {

    /** Run the behaviour, reporting each step to {@code trace}. */
    void run(Target target, AttackTrace trace);

    String name();
}
