package com.travel.ai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.travel.ai.config.AppEvalProperties;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测 HTTP 入口网关：仅校验与业务 membership 无关的 {@code X-Eval-Gateway-Key}，
 * 避免与 {@code X-Eval-Token}（参与 HMAC 派生）混用同一密钥语义。
 * <p>
 * 未配置 {@code app.eval.gateway-key} 时拒绝一切 {@code /api/v1/eval/**} 调用，防止误把评测口暴露在公网。
 */
@Component
public class EvalGatewayAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Eval-Gateway-Key";

    private final AppEvalProperties appEvalProperties;

    public EvalGatewayAuthFilter(AppEvalProperties appEvalProperties) {
        this.appEvalProperties = appEvalProperties;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/api/v1/eval/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String expectedGatewayKey = appEvalProperties.getGatewayKey();
        if (expectedGatewayKey.isBlank()) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "EVAL_GATEWAY_NOT_CONFIGURED",
                    "Set app.eval.gateway-key or environment APP_EVAL_GATEWAY_KEY.");
            return;
        }
        String provided = request.getHeader(HEADER);
        if (!constantTimeEquals(expectedGatewayKey, provided)) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "EVAL_GATEWAY_UNAUTHORIZED",
                    "Missing or invalid " + HEADER + ".");
            return;
        }
        // Spring Security 6：三参构造（含 authorities）已视为已认证，禁止再 setAuthenticated(true)，否则抛 IllegalArgumentException
        var auth = new UsernamePasswordAuthenticationToken(
                "eval-gateway",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_EVAL_GATEWAY"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = (provided == null ? "" : provided).getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        return MessageDigest.isEqual(a, b);
    }

    private static void writeJson(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, String> m = new LinkedHashMap<>();
        m.put("error", code);
        m.put("message", message);
        response.getWriter().write(new ObjectMapper().writeValueAsString(m));
    }
}
