package com.mirage;

import com.mirage.common.ScopeGuard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScopeGuardTest {

    private final ScopeGuard guard = new ScopeGuard(List.of("10.10.0.0/24"));

    @Test
    void allowsAddressInRange() {
        assertTrue(guard.isAuthorised("10.10.0.5"));
        assertDoesNotThrow(() -> guard.require("10.10.0.200"));
    }

    @Test
    void refusesAddressOutsideRange() {
        assertFalse(guard.isAuthorised("8.8.8.8"));
        assertThrows(SecurityException.class, () -> guard.require("192.168.1.1"));
    }

    @Test
    void refusesMalformedOrNonIpSource() {
        assertFalse(guard.isAuthorised("local-filesystem"));
        assertFalse(guard.isAuthorised("not.an.ip"));
        assertThrows(SecurityException.class, () -> guard.require("local-filesystem"));
    }

    @Test
    void acceptsPrivateAndLoopbackLabRanges() {
        assertDoesNotThrow(() -> ScopeGuard.requireLabRange(List.of("10.10.0.0/24")));
        assertDoesNotThrow(() -> ScopeGuard.requireLabRange(List.of("192.168.0.0/24", "127.0.0.1/32")));
        assertDoesNotThrow(() -> ScopeGuard.requireLabRange(List.of("172.16.5.0/24")));
    }

    @Test
    void refusesPublicLabRange() {
        assertThrows(IllegalArgumentException.class,
                () -> ScopeGuard.requireLabRange(List.of("8.8.8.0/24")));
        assertThrows(IllegalArgumentException.class,
                () -> ScopeGuard.requireLabRange(List.of("0.0.0.0/0")));
    }

    @Test
    void refusesOverBroadLabRange() {
        // Private, but a whole /8 is far too wide for a lab.
        assertThrows(IllegalArgumentException.class,
                () -> ScopeGuard.requireLabRange(List.of("10.0.0.0/8")));
    }

    @Test
    void refusesEmptyLabRange() {
        assertThrows(IllegalArgumentException.class, () -> ScopeGuard.requireLabRange(List.of()));
    }
}
