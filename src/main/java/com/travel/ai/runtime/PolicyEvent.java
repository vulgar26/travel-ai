package com.travel.ai.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 共享的“策略/决策事件”模型：用于把安全门控、RAG 门控、工具治理、断点等决策以结构化方式输出（SSE）或汇总（eval meta）。
 * <p>
 * 字段尽量对齐 eval 的 {@code meta.policy_events[]}，但不依赖 Jackson，避免主线热路径引入额外序列化成本。
 */
public record PolicyEvent(
        String policyType,
        String stage,
        String behavior,
        String ruleId,
        String errorCode,
        String requestId,
        Map<String, String> attrs
) {

    public static PolicyEvent of(
            String policyType,
            String stage,
            String behavior,
            String ruleId,
            String errorCode,
            String requestId
    ) {
        return new PolicyEvent(policyType, stage, behavior, ruleId, errorCode, requestId, Map.of());
    }

    public PolicyEvent withAttr(String k, String v) {
        if (k == null || k.isBlank() || v == null) {
            return this;
        }
        Map<String, String> a = new LinkedHashMap<>(attrs != null ? attrs : Map.of());
        a.put(k, v);
        return new PolicyEvent(policyType, stage, behavior, ruleId, errorCode, requestId, a);
    }

    public String toSseJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (policyType != null && !policyType.isBlank()) out.put("policy_type", policyType);
        if (stage != null && !stage.isBlank()) out.put("stage", stage);
        if (behavior != null && !behavior.isBlank()) out.put("behavior", behavior);
        if (ruleId != null && !ruleId.isBlank()) out.put("rule_id", ruleId);
        if (errorCode != null && !errorCode.isBlank()) out.put("error_code", errorCode);
        if (requestId != null && !requestId.isBlank()) out.put("request_id", requestId);
        if (attrs != null && !attrs.isEmpty()) out.put("attrs", attrs);
        return toJsonObject(out);
    }

    private static String toJsonObject(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append('"').append(':');
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else if (v instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) m;
                sb.append(toJsonObject(nested));
            } else {
                sb.append('"').append(escape(String.valueOf(v))).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}

