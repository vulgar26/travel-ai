package com.travel.ai.tools;

public record ToolResult(
        String name,
        boolean required,
        boolean used,
        boolean succeeded,
        ToolOutcome outcome,
        String errorCode,
        long latencyMs,
        String observationSummary,
        boolean observationTruncated
) {
    public static ToolResult disabledByPolicy(String name, boolean required, String errorCode) {
        return new ToolResult(
                name,
                required,
                false,
                false,
                ToolOutcome.DISABLED_BY_POLICY,
                errorCode,
                0L,
                null,
                false
        );
    }

    public static ToolResult disabledByCircuitBreaker(String name, boolean required, String errorCode) {
        return new ToolResult(
                name,
                required,
                false,
                false,
                ToolOutcome.DISABLED_BY_CIRCUIT_BREAKER,
                errorCode,
                0L,
                null,
                false
        );
    }

    public static ToolResult rateLimited(String name, boolean required, String errorCode) {
        return new ToolResult(
                name,
                required,
                false,
                false,
                ToolOutcome.RATE_LIMITED,
                errorCode,
                0L,
                null,
                false
        );
    }

    /** 与 {@link ToolOutcome#DISABLED_BY_CIRCUIT_BREAKER} 对齐，供观测字段映射。 */
    public boolean disabledByCircuitBreaker() {
        return outcome == ToolOutcome.DISABLED_BY_CIRCUIT_BREAKER;
    }

    /** 与 {@link ToolOutcome#RATE_LIMITED} 对齐，供观测字段映射。 */
    public boolean rateLimited() {
        return outcome == ToolOutcome.RATE_LIMITED;
    }
}

