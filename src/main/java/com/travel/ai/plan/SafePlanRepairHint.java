package com.travel.ai.plan;

import java.util.List;

/**
 * 传给 {@link PlanRepairModelPort} 的修复上下文：仅结构化摘要 + schema 片段，禁止携带用户 query、工具输出、KB 片段或完整 plan 原文。
 * <p>
 * 对齐 {@code plans/travel-ai-upgrade.md} P0-2「修复提示回显限制」。
 */
public record SafePlanRepairHint(String errorCode, List<PlanRepairViolation> violations, String schemaExcerpt) {

    /** 用于审计/测试：拼接 violations 与 error_code，不得包含 plan 原文字段。 */
    public String toCompactString() {
        StringBuilder sb = new StringBuilder();
        sb.append("error_code=").append(errorCode);
        for (PlanRepairViolation v : violations) {
            sb.append('|').append(v.path()).append(':').append(v.rule()).append(':').append(v.message());
        }
        if (schemaExcerpt != null && !schemaExcerpt.isEmpty()) {
            sb.append("|schema_len=").append(schemaExcerpt.length());
        }
        return sb.toString();
    }
}
