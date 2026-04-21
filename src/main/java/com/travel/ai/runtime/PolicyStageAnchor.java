package com.travel.ai.runtime;

/**
 * 策略/决策事件在流水线中的“锚点”（stage anchor）。
 * <p>
 * 说明：这不是线性编排阶段（PLAN/RETRIEVE/…），而是“策略发生在什么时候”的稳定位置，用于：
 * <ul>
 *   <li>eval：写入 {@code meta.policy_events[].stage}</li>
 *   <li>主线 SSE：写入 {@code event: policy} 的 {@code stage}</li>
 * </ul>
 */
public enum PolicyStageAnchor {
    /** plan 解析与物理阶段决策前 */
    PRE_PLAN("pre_plan"),
    /** plan 解析后、检索前 */
    POST_PLAN("post_plan"),
    /** 检索后、写作前（常见：RAG 门控、行为策略） */
    POST_RETRIEVE("post_retrieve"),
    /** 工具阶段 */
    TOOL("tool");

    private final String wireValue;

    PolicyStageAnchor(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}

