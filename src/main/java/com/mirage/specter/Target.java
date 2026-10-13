package com.mirage.specter;

/** A single attack target: always validated against ScopeGuard before use. */
public record Target(String host, int port) {
}
