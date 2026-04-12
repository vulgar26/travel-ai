package com.travel.ai.eval.planrepair;

/**
 * 修复提示中的单条违规描述（结构化摘要，不含业务原文）。
 */
public record PlanRepairViolation(String path, String rule, String expectedType, String message) {

    public PlanRepairViolation(String path, String rule, String message) {
        this(path, rule, null, message);
    }
}
