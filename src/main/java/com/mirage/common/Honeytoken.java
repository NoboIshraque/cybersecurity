package com.mirage.common;

/**
 * A planted fake credential. It exists nowhere a legitimate user would find it,
 * so if its credentials ever appear in a captured login attempt, the source that
 * holds it has been compromised.
 */
public record Honeytoken(String id, String credentials, String plantedLocation) {
}
