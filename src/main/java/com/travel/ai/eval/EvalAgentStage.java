package com.travel.ai.eval;

/**
 * 评测用 Agent 线性阶段枚举，与 {@code plans/p0-execution-map.md} 附录 E 中 {@code steps[*].stage} 取值一致。
 * <p>
 * P0 禁止 DAG / 回环 / 并行工具：执行器只应按 {@link EvalLinearAgentPipeline} 中声明的固定顺序串行经过各常量。
 */
public enum EvalAgentStage {
    PLAN,
    RETRIEVE,
    TOOL,
    WRITE,
    GUARD
}
