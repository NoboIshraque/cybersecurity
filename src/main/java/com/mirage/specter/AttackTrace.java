package com.mirage.specter;

import com.mirage.common.trace.TraceLevel;

/**
 * A per-strategy trace callback: the strategy reports each step as it happens and
 * the {@link Attacker} forwards it to the right shell (channel and target already
 * bound). The default does nothing, so tests and headless runs pay nothing.
 */
@FunctionalInterface
public interface AttackTrace {

    void line(TraceLevel level, String text);

    AttackTrace NONE = (level, text) -> { };
}
