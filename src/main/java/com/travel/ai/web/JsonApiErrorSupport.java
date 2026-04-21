package com.travel.ai.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 与 {@link com.travel.ai.security.EvalGatewayAuthFilter}、{@link KnowledgeControllerAdvice} 对齐的
 * {@code { "error", "message" }} JSON 错误体（工程债：鉴权与 REST 边界统一）。
 */
public final class JsonApiErrorSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonApiErrorSupport() {
    }

    public static void write(HttpServletResponse response, int status, String error, String message)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, String> m = new LinkedHashMap<>();
        m.put("error", error);
        m.put("message", message);
        response.getWriter().write(MAPPER.writeValueAsString(m));
        response.flushBuffer();
    }
}
