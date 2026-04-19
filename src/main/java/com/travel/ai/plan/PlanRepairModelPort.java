package com.travel.ai.plan;

/**
 * 将结构化修复提示交给「模型侧」并拿回<strong>纯 JSON</strong> plan 文本（Day5 默认实现为罐装合法 JSON）。
 */
@FunctionalInterface
public interface PlanRepairModelPort {

    /**
     * @param hint 仅含 error_code、violations、schema 片段，不得依赖调用方传入用户/工具/KB 原文
     * @return 供 {@link PlanParser} 再次解析的 JSON 字符串
     */
    String repair(SafePlanRepairHint hint);
}
