package com.mirage.common;

import java.util.ArrayList;
import java.util.List;

/**
 * The safety boundary of the whole system.
 *
 * <p>Every outbound action — a Specter simulated attack or an active-defence
 * response — must pass {@link #require(String)} first. Only addresses inside
 * the configured lab range are ever authorised; everything else is refused.
 * The default is to deny. Do NOT weaken this class: it is what keeps Mirage a
 * lawful, lab-only tool.
 */
public final class ScopeGuard {

    private record Cidr(long network, long mask) {
        boolean contains(long ip) {
            return (ip & mask) == (network & mask);
        }
    }

    private final List<Cidr> allowed = new ArrayList<>();

    public ScopeGuard(List<String> cidrs) {
        for (String cidr : cidrs) {
            allowed.add(parse(cidr));
        }
    }

    public boolean isAuthorised(String ip) {
        long value = toLong(ip);
        return allowed.stream().anyMatch(c -> c.contains(value));
    }

    public void require(String ip) {
        if (!isAuthorised(ip)) {
            throw new SecurityException(ip + " is outside the authorised lab range");
        }
    }

    private static Cidr parse(String cidr) {
        String[] parts = cidr.split("/");
        long network = toLong(parts[0]);
        int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : 32;
        long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return new Cidr(network, mask);
    }

    private static long toLong(String ip) {
        String[] octets = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) | (Integer.parseInt(octets[i]) & 0xFF);
        }
        return result;
    }
}
