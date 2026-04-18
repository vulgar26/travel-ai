package com.travel.ai.tools;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolCircuitBreaker {

    private final int failureThreshold;
    private final long cooldownMs;

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public ToolCircuitBreaker(
            @Value("${app.tools.cb.failure-threshold:3}") int failureThreshold,
            @Value("${app.tools.cb.cooldown-ms:30000}") long cooldownMs
    ) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldownMs = Math.max(1000L, cooldownMs);
    }

    public boolean allow(String toolName) {
        // 生产链路使用真实时钟；单测用 allowAt/recordFailureAt 固定时间轴，避免 sleep 带来不稳定。
        return allowAt(toolName, System.currentTimeMillis());
    }

    /** 包内测试：固定 {@code nowMs} 判定半开窗口，避免依赖真实时钟。 */
    boolean allowAt(String toolName, long nowMs) {
        State s = states.get(toolName);
        if (s == null) {
            return true;
        }
        long until = s.openUntilMs;
        if (until <= 0) {
            return true;
        }
        if (nowMs >= until) {
            states.remove(toolName, s);
            return true;
        }
        return false;
    }

    public void recordSuccess(String toolName) {
        states.remove(toolName);
    }

    public void recordFailure(String toolName) {
        // 生产链路使用真实时钟；单测用 recordFailureAt 固定 nowMs。
        recordFailureAt(toolName, System.currentTimeMillis());
    }

    /** 包内测试：与 {@link #allowAt(String, long)} 共用同一时间轴。 */
    void recordFailureAt(String toolName, long nowMs) {
        states.compute(toolName, (k, v) -> {
            if (v == null) {
                v = new State();
            }
            v.failureCount++;
            if (v.failureCount >= failureThreshold) {
                v.openUntilMs = nowMs + cooldownMs;
            }
            return v;
        });
    }

    private static final class State {
        int failureCount = 0;
        long openUntilMs = 0L;
    }
}

