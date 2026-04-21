package com.travel.ai.eval;

/**
 * 历史枚举：已迁移到共享的 {@link com.travel.ai.runtime.StageName}。
 *
 * @deprecated 使用 {@link com.travel.ai.runtime.StageName}
 */
@Deprecated
public enum EvalAgentStage {
    PLAN,
    RETRIEVE,
    TOOL,
    GUARD,
    WRITE
}
