package com.mirage.sensor;

import com.mirage.common.ResponseCommand;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Holds the active-defence state pushed by the Console and gates connections. */
public final class DefenceState implements ConnectionGate {

    private final Set<String> blocked = ConcurrentHashMap.newKeySet();
    private final Set<String> tarpitted = ConcurrentHashMap.newKeySet();

    public void apply(ResponseCommand command) {
        switch (command.type()) {
            case BLOCK -> blocked.add(command.targetIp());
            case TARPIT, FEED_FAKE -> tarpitted.add(command.targetIp());
            case UNBLOCK -> {
                blocked.remove(command.targetIp());
                tarpitted.remove(command.targetIp());
            }
        }
    }

    @Override
    public Decision decide(String sourceIp) {
        if (blocked.contains(sourceIp)) {
            return Decision.BLOCK;
        }
        if (tarpitted.contains(sourceIp)) {
            return Decision.TARPIT;
        }
        return Decision.ALLOW;
    }

    public boolean isBlocked(String sourceIp) {
        return blocked.contains(sourceIp);
    }

    public boolean isTarpitted(String sourceIp) {
        return tarpitted.contains(sourceIp);
    }
}
