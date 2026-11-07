package com.mirage.specter;

/**
 * A single attack target: always validated against ScopeGuard before use. The port must be a
 * real TCP port (1-65535); anything else is refused here, before a launch, instead of failing
 * later inside a strategy on a pool thread (B-20).
 */
public record Target(String host, int port) {

    public static final int MIN_PORT = 1;
    public static final int MAX_PORT = 65535;

    public Target {
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException("port " + port + " is not a TCP port ("
                    + MIN_PORT + "-" + MAX_PORT + ")");
        }
    }
}
