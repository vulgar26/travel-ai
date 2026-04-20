package com.travel.ai.llm;

/**
 * 供应商 usage 的统一口径（若可获得）。
 * <p>
 * 约束：用于观测/对账；不同供应商的 token 计费口径可能存在差异。
 */
public record LlmUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens, String source) {
}

