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
}
