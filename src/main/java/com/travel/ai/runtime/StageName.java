package com.travel.ai.runtime;

/**
 * 线性编排阶段名：用于在主线 SSE 与 eval 输出中对齐同一套“阶段语义”。
 * <p>
 * 约定：字符串序列化时使用 {@link #name()}（全大写），与现有 {@code stage_order} 兼容。
 */
public enum StageName {
    PLAN,
    RETRIEVE,
    TOOL,
    GUARD,
    WRITE
}

