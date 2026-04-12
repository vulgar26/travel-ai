package com.travel.ai.plan;

/**
 * 附录 E 中 {@code steps[*].stage} 枚举。
 */
public enum PlanStage {
    PLAN,
    RETRIEVE,
    TOOL,
    WRITE,
    GUARD
}
