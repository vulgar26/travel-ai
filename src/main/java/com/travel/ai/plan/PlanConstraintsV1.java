package com.travel.ai.plan;

/**
 * 附录 E 中 {@code constraints}：必填 {@code max_steps}、{@code total_timeout_ms}、{@code tool_timeout_ms}。
 */
public record PlanConstraintsV1(int maxSteps, int totalTimeoutMs, int toolTimeoutMs) {
}
