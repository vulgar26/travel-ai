package com.travel.ai.eval.planrepair;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.travel.ai.plan.PlanParseException;

import java.util.List;

/**
 * 从 {@link PlanParseException} 构造 {@link SafePlanRepairHint}，避免把不可信原文塞进修复面。
 */
public final class SafePlanRepairHintBuilder {

    private static final int MAX_VIOLATION_MESSAGE_LEN = 240;
    private static final int MAX_SCHEMA_EXCERPT_LEN = 2048;

    /**
     * 附录 E 骨架的最小说明（非完整 Schema 文件），仅含字段名与类型意图。
     */
    private static final String SCHEMA_EXCERPT_BASE = """
            plan_version:string(v1); goal?:string; steps:array(1-8);\
             each step: step_id, stage in PLAN|RETRIEVE|TOOL|WRITE|GUARD, instruction;\
             optional tool{name,args object}; optional expected_output;\
             constraints:{max_steps:int(1-20),total_timeout_ms:int,tool_timeout_ms:int}; notes?:string\
            """.replaceAll("\\s+", " ").trim();

    private SafePlanRepairHintBuilder() {
    }

    public static SafePlanRepairHint from(PlanParseException e) {
        String safeMessage;
        String rule;
        if (e.getCause() instanceof JsonProcessingException) {
            rule = "JSON_SYNTAX";
            safeMessage = "Input is not valid JSON (details omitted for safety)";
        } else {
            rule = "SCHEMA";
            safeMessage = truncate(stripNewlines(e.getMessage()), MAX_VIOLATION_MESSAGE_LEN);
        }
        PlanRepairViolation v = new PlanRepairViolation("$", rule, safeMessage);
        String excerpt = truncate(SCHEMA_EXCERPT_BASE, MAX_SCHEMA_EXCERPT_LEN);
        return new SafePlanRepairHint("PARSE_ERROR", List.of(v), excerpt);
    }

    private static String stripNewlines(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }
}
