package com.travel.ai.tools;

public enum ToolOutcome {
    OK,
    TIMEOUT,
    ERROR,
    DISABLED_BY_POLICY,
    DISABLED_BY_CIRCUIT_BREAKER,
    RATE_LIMITED
}

