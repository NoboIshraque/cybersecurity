package com.mirage.common;

import java.util.ArrayList;
import java.util.List;

/**
 * The running dossier for one source address. Mutated only by the single
 * correlation consumer thread, so it needs no internal synchronisation.
 */
public final class AttackerProfile {

    private final String sourceIp;
    private final List<AttackEvent> events = new ArrayList<>();

    public AttackerProfile(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public void add(AttackEvent event) {
        events.add(event);
    }

    public String sourceIp() {
        return sourceIp;
    }

    public List<AttackEvent> events() {
        return events;
    }
}
