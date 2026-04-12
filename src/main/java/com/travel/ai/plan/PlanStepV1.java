package com.travel.ai.plan;

/**
 * 附录 E 中 {@code steps[]} 的单项。
 */
public record PlanStepV1(
        String stepId,
        PlanStage stage,
        String instruction,
        PlanToolV1 tool,
        String expectedOutput
) {
    public boolean hasTool() {
        return tool != null;
    }
}
