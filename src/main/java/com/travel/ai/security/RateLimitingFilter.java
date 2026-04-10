package com.travel.ai.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Bucket4j + Caffeine 的轻量限流过滤器。
 * <p>
 * <b>两类互不影响的配额</b>（与 {@code docs/UPGRADE_PLAN.md} 中 P0-1 / P1-1 对齐）：
 * <ul>
 *   <li><b>聊天 SSE</b>：路径前缀 {@code /travel/chat}，按「已登录用户名」或「匿名 IP」限流，
 *       防止对话接口刷爆 LLM/向量库。</li>
 *   <li><b>登录</b>：{@code POST /auth/login}，<b>仅按 IP</b> 限流（登录前尚无可靠用户身份），
 *       缓解对演示账号或未来用户表的暴力尝试。</li>
 * </ul>
 * 每分钟次数由 {@code application.yml} 中 {@code app.rate-limit.*} 配置，便于与 README/架构文档对齐。
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    /** 聊天接口：每分钟允许的请求数（构造 Bandwidth 时用，在 {@link #init()} 中初始化） */
    @Value("${app.rate-limit.chat.requests-per-minute:5}")
    private int chatRequestsPerMinute;

    /** 登录接口：每个 IP 每分钟允许的 POST 次数 */
    @Value("${app.rate-limit.login.requests-per-minute:20}")
    private int loginRequestsPerMinute;

    /** 聊天专用 token 桶策略（greedy  refill：每分钟重置一整桶额度） */
    private Bandwidth chatBandwidth;

    /** 登录专用 token 桶策略 */
    private Bandwidth loginBandwidth;

    /**
     * 共用一块 Caffeine 缓存存放不同 key 的 Bucket；key 带前缀避免聊天与登录统计混用。
     * expireAfterAccess：一段时间无访问则淘汰，防止长期运行内存膨胀。
     */
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    @PostConstruct
    void init() {
        // 将可配置次数转为 Bucket4j 的 Bandwidth：classic(capacity, refill)
        this.chatBandwidth = Bandwidth.classic(
                Math.max(1, chatRequestsPerMinute),
                Refill.greedy(Math.max(1, chatRequestsPerMinute), Duration.ofMinutes(1)));
        this.loginBandwidth = Bandwidth.classic(
                Math.max(1, loginRequestsPerMinute),
                Refill.greedy(Math.max(1, loginRequestsPerMinute), Duration.ofMinutes(1)));
        log.info("[rate-limit] 已初始化：chat={}/分钟/用户或IP, login={}/分钟/IP",
                chatRequestsPerMinute, loginRequestsPerMinute);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 1) 登录：仅拦截 POST，避免误伤 GET 预检或其它动词（当前登录仅为 POST JSON）
        if ("/auth/login".equals(path) && "POST".equalsIgnoreCase(request.getMethod())) {
            if (!tryConsumeLogin(request)) {
                write429(response);
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        // 2) 聊天 SSE：路径以 /travel/chat 开头（含 /travel/chat/{id}）
        if (path.startsWith("/travel/chat")) {
            if (!tryConsumeChat(request)) {
                write429(response);
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        // 其它请求不参与本过滤器配额
        filterChain.doFilter(request, response);
    }

    /**
     * 消费「聊天」额度：JwtAuthFilter 已先执行时，此处可读到已认证用户名。
     */
    private boolean tryConsumeChat(HttpServletRequest request) {
        String key = "chat:" + resolveChatRateLimitKey(request);
        Bucket bucket = buckets.get(key, k -> Bucket.builder().addLimit(chatBandwidth).build());
        boolean ok = bucket.tryConsume(1);
        if (!ok) {
            log.warn("[rate-limit] 聊天接口超限 key={}", key);
        }
        return ok;
    }

    /**
     * 消费「登录」额度：统一按客户端 IP，不把用户名放进 key（防止日志侧泄露尝试账号）。
     */
    private boolean tryConsumeLogin(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String key = "login:ip:" + ip;
        Bucket bucket = buckets.get(key, k -> Bucket.builder().addLimit(loginBandwidth).build());
        boolean ok = bucket.tryConsume(1);
        if (!ok) {
            log.warn("[rate-limit] 登录接口超限 key={}", key);
        }
        return ok;
    }

    private void write429(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("""
                {"code":"RATE_LIMITED","message":"请求过于频繁，请稍后再试"}
                """);
    }

    /**
     * 与 JwtAuthFilter 的顺序配合：限流在 JWT 之后，已登录用户用 user:username，匿名退化为 ip:x.x.x.x。
     */
    private String resolveChatRateLimitKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return "user:" + auth.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
