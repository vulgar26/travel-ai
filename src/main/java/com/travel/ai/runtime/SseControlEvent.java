package com.travel.ai.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSE 控制事件（主线用户体验层）：用于告诉客户端“本轮结束(done)”或“本轮失败(error)”，并提供结构化原因。
 * <p>
 * 说明：这类事件是 SSE 协议层的补充，不等同于业务文本 chunk。
 */
public record SseControlEvent(
        String type,
        String requestId,
        String errorCode,
        String message,
        Map<String, String> attrs
) {

    public static SseControlEvent done(String requestId) {
        return new SseControlEvent("done", requestId, null, null, Map.of());
    }

    public static SseControlEvent error(String requestId, String errorCode, String message) {
        return new SseControlEvent("error", requestId, errorCode, message, Map.of());
    }

    public String toSseJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", type != null ? type : "");
        out.put("request_id", requestId != null ? requestId : "");
        if (errorCode != null && !errorCode.isBlank()) out.put("error_code", errorCode);
        if (message != null && !message.isBlank()) out.put("message", message);
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

