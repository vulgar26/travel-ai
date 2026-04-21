package com.travel.ai.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 共享的“阶段事件”模型（A 粒度）：用于把固定线性编排的过程以结构化方式输出（SSE）或汇总（eval meta）。
 * <p>
 * 设计目标：
 * <ul>
 *   <li>足够小：不依赖 Jackson 等序列化器，避免在 agent 热路径引入额外 Bean/配置。</li>
 *   <li>足够稳定：字段名尽量与现有日志 / eval meta 语义一致（stage / request_id / elapsed_ms / error_code）。</li>
 * </ul>
 */
public record StageEvent(
        StageEventKind kind,
        StageName stage,
        String requestId,
        Long elapsedMs,
        String errorCode,
        String message,
        Map<String, String> attrs
) {

    public static StageEvent start(StageName stage, String requestId) {
        return new StageEvent(StageEventKind.START, stage, requestId, null, null, null, Map.of());
    }

    public static StageEvent end(StageName stage, String requestId, long elapsedMs) {
        return new StageEvent(StageEventKind.END, stage, requestId, elapsedMs, null, null, Map.of());
    }

    public static StageEvent skip(StageName stage, String requestId, String reason) {
        Map<String, String> a = new LinkedHashMap<>();
        if (reason != null && !reason.isBlank()) {
            a.put("reason", reason);
        }
        return new StageEvent(StageEventKind.SKIP, stage, requestId, null, null, null, a);
    }

    /**
     * 轻量 JSON 序列化（SSE data 字段用）；仅处理本模型需要的最小转义。
     */
    public String toSseJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", kind != null ? kind.name() : "");
        out.put("stage", stage != null ? stage.name() : "");
        out.put("request_id", requestId != null ? requestId : "");
        if (elapsedMs != null) {
            out.put("elapsed_ms", elapsedMs);
        }
        if (errorCode != null && !errorCode.isBlank()) {
            out.put("error_code", errorCode);
        }
        if (message != null && !message.isBlank()) {
            out.put("message", message);
        }
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
                // only used for attrs: Map<String,String>
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

