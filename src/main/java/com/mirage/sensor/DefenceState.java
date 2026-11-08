package com.mirage.sensor;

import com.mirage.common.ResponseCommand;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the active-defence state pushed by the Console and gates connections.
 *
 * <p>A blocked source is always blocked. Otherwise a source may be <em>diverted</em> in one of
 * two ways: tarpitted (held and stalled) or fed fake data (let "in" and shown lures, B-09). The
 * two exclude each other, so the operator's latest choice between them wins: tarpit then
 * fake data feeds fake data, and the other way round. Unblock clears everything.
 */
public final class DefenceState implements ConnectionGate {

    private final Set<String> blocked = ConcurrentHashMap.newKeySet();
    /** Source → TARPIT or FEED_FAKE. */
    private final Map<String, Decision> diverted = new ConcurrentHashMap<>();

    public void apply(ResponseCommand command) {
        String ip = command.targetIp();
        switch (command.type()) {
            case BLOCK -> blocked.add(ip);
            case TARPIT -> diverted.put(ip, Decision.TARPIT);
            case FEED_FAKE -> diverted.put(ip, Decision.FEED_FAKE);
            case UNBLOCK -> {
                blocked.remove(ip);
                diverted.remove(ip);
            }
        }
    }

    @Override
    public Decision decide(String sourceIp) {
        if (blocked.contains(sourceIp)) {
            return Decision.BLOCK;
        }
        return diverted.getOrDefault(sourceIp, Decision.ALLOW);
    }

    public boolean isBlocked(String sourceIp) {
        return blocked.contains(sourceIp);
    }

    public boolean isTarpitted(String sourceIp) {
        return diverted.get(sourceIp) == Decision.TARPIT;
    }

    public boolean isFedFakeData(String sourceIp) {
        return diverted.get(sourceIp) == Decision.FEED_FAKE;
    }
}
