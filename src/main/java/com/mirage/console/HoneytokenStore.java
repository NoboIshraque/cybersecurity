package com.mirage.console;

import com.mirage.common.Honeytoken;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Holds the planted honeytokens and matches captured credentials against them. */
public final class HoneytokenStore {

    private final List<Honeytoken> tokens = new CopyOnWriteArrayList<>();

    public void plant(Honeytoken token) {
        tokens.add(token);
    }

    /** The planted token whose credentials equal {@code credentials}, if any. */
    public Optional<Honeytoken> match(String credentials) {
        return tokens.stream()
                .filter(t -> t.credentials().equals(credentials))
                .findFirst();
    }

    public List<Honeytoken> all() {
        return List.copyOf(tokens);
    }
}
