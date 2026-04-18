package com.travel.ai.tools;

import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutorTest {

    @Test
    void disabledByPolicy_shouldNotCall() {
        ToolResult r = ToolExecutor.execute("weather", true, false, 100, () -> {
            throw new AssertionError("should not be called");
        });
        assertEquals("weather", r.name());
        assertTrue(r.required());
        assertFalse(r.used());
        assertFalse(r.succeeded());
        assertEquals(ToolOutcome.DISABLED_BY_POLICY, r.outcome());
        assertEquals(ToolExecutor.ERROR_CODE_POLICY_DISABLED, r.errorCode());
    }

    @Test
    void ok_shouldSummarizeAndTruncate() {
        ToolResult r = ToolExecutor.execute("weather", true, true, 5, () -> " 123456789 ");
        assertTrue(r.used());
        assertTrue(r.succeeded());
        assertEquals(ToolOutcome.OK, r.outcome());
        assertNull(r.errorCode());
        assertEquals("12345…", r.observationSummary());
        assertTrue(r.observationTruncated());
        assertTrue(r.latencyMs() >= 0);
    }

    @Test
    void timeout_shouldMapToTimeoutOutcomeAndErrorCode() {
        ToolResult r = ToolExecutor.execute("weather", true, true, 100, () -> {
            throw new RuntimeException(new SocketTimeoutException("timeout"));
        });
        assertTrue(r.used());
        assertFalse(r.succeeded());
        assertEquals(ToolOutcome.TIMEOUT, r.outcome());
        assertEquals(ToolExecutor.ERROR_CODE_TOOL_TIMEOUT, r.errorCode());
    }

    @Test
    void error_shouldMapToErrorOutcomeAndErrorCode() {
        ToolResult r = ToolExecutor.execute("weather", true, true, 100, () -> {
            throw new RuntimeException("boom");
        });
        assertTrue(r.used());
        assertFalse(r.succeeded());
        assertEquals(ToolOutcome.ERROR, r.outcome());
        assertEquals(ToolExecutor.ERROR_CODE_TOOL_ERROR, r.errorCode());
    }

    @Test
    void governanceFactories_exposeOutcomeHelpers() {
        ToolResult cb = ToolResult.disabledByCircuitBreaker("w", true, "TOOL_DISABLED_BY_CIRCUIT_BREAKER");
        assertTrue(cb.disabledByCircuitBreaker());
        assertFalse(cb.rateLimited());

        ToolResult rl = ToolResult.rateLimited("w", true, "RATE_LIMITED");
        assertTrue(rl.rateLimited());
        assertFalse(rl.disabledByCircuitBreaker());
    }
}

