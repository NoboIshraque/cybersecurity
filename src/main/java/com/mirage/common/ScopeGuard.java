package com.mirage.common;

import java.util.ArrayList;
import java.util.List;

/**
 * The safety boundary of the whole system.
 *
 * <p>Every outbound action — a Specter simulated attack or an active-defence
 * response — must pass {@link #require(String)} first. Only addresses inside
 * the configured lab range are ever authorised; everything else, including a
 * malformed or non-IP source, is refused. The default is to deny. Do NOT weaken
 * this class: it is what keeps Mirage a lawful, lab-only tool.
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
        long value;
        try {
            value = toLong(ip);
        } catch (RuntimeException e) {
            return false; // malformed or non-IP source is never authorised
        }
        return allowed.stream().anyMatch(c -> c.contains(value));
    }

    public void require(String ip) {
        if (!isAuthorised(ip)) {
            throw new SecurityException(ip + " is outside the authorised lab range");
        }
    }

    /** The private/loopback blocks a lab range is allowed to sit inside (RFC 1918 + loopback). */
    private static final List<String> PRIVATE_BLOCKS =
            List.of("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8");

    /**
     * Guard against a dangerously wide or public lab range. Every configured CIDR must sit
     * inside a private (RFC 1918) or loopback block and be no broader than /16, so the system
     * can never be aimed at the public internet or a whole /8. The entry points call this at
     * start-up, so a bad range stops the app before any socket opens. It is a hard safety check
     * in addition to {@link #require(String)} default-deny; do NOT weaken it.
     */
    public static void requireLabRange(List<String> cidrs) {
        if (cidrs == null || cidrs.isEmpty()) {
            throw new IllegalArgumentException("lab range is empty: refusing to start with no scope");
        }
        for (String cidr : cidrs) {
            validateLabCidr(cidr);
        }
    }

    private static void validateLabCidr(String cidr) {
        int prefix;
        Cidr parsed;
        try {
            String[] parts = cidr.split("/");
            prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : 32;
            parsed = parse(cidr);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("not a valid CIDR: " + cidr);
        }
        if (prefix < 16 || prefix > 32) {
            throw new IllegalArgumentException(
                    "lab range " + cidr + " is too broad: prefix must be between /16 and /32");
        }
        for (String block : PRIVATE_BLOCKS) {
            if (parse(block).contains(parsed.network())) {
                return; // inside a private/loopback block — OK
            }
        }
        throw new IllegalArgumentException("lab range " + cidr + " is not a private or loopback range "
                + "(must be within 10/8, 172.16/12, 192.168/16, or 127/8)");
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
        if (octets.length != 4) {
            throw new IllegalArgumentException("not an IPv4 address: " + ip);
        }
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) | (Integer.parseInt(octets[i]) & 0xFF);
        }
        return result;
    }
}
