package com.travel.ai.eval;

import java.util.List;

/**
 * 评测口 {@code llm_mode=real} 的 usage 探针是否允许触发：默认要求 {@code eval_tags} 命中配置的前缀（如 {@code cost/}），
 * 避免跑批全量对外网计费。
 */
public final class EvalLlmRealTagPolicy {

    private EvalLlmRealTagPolicy() {
    }

    /**
     * @param requireTagMatch {@code false} 时不校验标签
     * @param prefixes        非空前缀列表（{@code requireTagMatch=true} 时由配置解析得到）
     * @param evalTags        请求体 {@code eval_tags}
     * @return {@code null} 表示通过；否则为写入 {@code meta.provider_usage_failure_reason} 的归因码
     */
    public static String tagGateFailureReasonOrNull(boolean requireTagMatch, List<String> prefixes, List<String> evalTags) {
        if (!requireTagMatch) {
            return null;
        }
        if (prefixes == null || prefixes.isEmpty()) {
            return null;
        }
        if (evalTags == null || evalTags.isEmpty()) {
            return "tag_gate_no_tags";
        }
        for (String tag : evalTags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            for (String prefix : prefixes) {
                if (prefix != null && !prefix.isBlank() && tag.startsWith(prefix)) {
                    return null;
                }
            }
        }
        return "tag_gate_no_match";
    }
}
