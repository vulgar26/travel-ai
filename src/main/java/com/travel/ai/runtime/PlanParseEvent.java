package com.travel.ai.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 共享的 “plan parse 元事件”：主线 SSE 与 eval meta 口径对齐的最小字段集合。
 */
public record PlanParseEvent(
        String planParseOutcome,
        int planParseAttempts,
        String planDraftSource,
        String planParseResolved,
        String requestId,
        Map<String, String> attrs
) {

    public static PlanParseEvent of(
            String planParseOutcome,
            int planParseAttempts,
            String planDraftSource,
            String planParseResolved,
            String requestId
    ) {
        return new PlanParseEvent(planParseOutcome, planParseAttempts, planDraftSource, planParseResolved, requestId, Map.of());
    }

    public String toSseJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plan_parse_outcome", planParseOutcome != null ? planParseOutcome : "");
        out.put("plan_parse_attempts", planParseAttempts);
        out.put("plan_draft_source", planDraftSource != null ? planDraftSource : "");
        out.put("plan_parse_resolved", planParseResolved != null ? planParseResolved : "");
        out.put("request_id", requestId != null ? requestId : "");
        if (attrs != null && !attrs.isEmpty()) {
            out.put("attrs", attrs);
        }
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

