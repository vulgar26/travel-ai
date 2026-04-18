package com.travel.ai.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCircuitBreakerTest {

    @Test
    void opensAfterThreshold_thenRecoversAfterCooldown() {
        ToolCircuitBreaker cb = new ToolCircuitBreaker(2, 10_000L);
        String t = "weather";
        long t0 = 1_000_000L;

        assertTrue(cb.allowAt(t, t0));
        cb.recordFailureAt(t, t0);
        assertTrue(cb.allowAt(t, t0));

        cb.recordFailureAt(t, t0);
        assertFalse(cb.allowAt(t, t0));

        long afterCooldown = t0 + 10_000L;
        assertTrue(cb.allowAt(t, afterCooldown));
    }

    @Test
    void successClearsFailureState() {
        ToolCircuitBreaker cb = new ToolCircuitBreaker(3, 60_000L);
        String t = "x";
        long t0 = 5_000_000L;
        cb.recordFailureAt(t, t0);
        cb.recordFailureAt(t, t0);
        cb.recordSuccess(t);
        cb.recordFailureAt(t, t0);
        assertTrue(cb.allowAt(t, t0));
    }
}
