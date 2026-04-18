package com.travel.ai.tools;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolRateLimiter {

    private final int limitPerMinute;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public ToolRateLimiter(@Value("${app.tools.rate-limit.per-minute:60}") int limitPerMinute) {
        this.limitPerMinute = Math.max(1, limitPerMinute);
    }

    public boolean tryAcquire(String toolName) {
        // 固定窗口的分钟桶：实现简单、统计口径清晰。
        // 单测用 tryAcquireAt 固定 nowMs，验证“同一分钟内到上限拒绝/跨分钟重置”。
        return tryAcquireAt(toolName, System.currentTimeMillis());
    }

    /** 包内测试：固定 {@code nowMs} 所在分钟桶，验证窗口切换与上限。 */
    boolean tryAcquireAt(String toolName, long nowMs) {
        long minute = nowMs / 60_000L;
        Window w = windows.compute(toolName, (k, v) -> {
            if (v == null || v.minute != minute) {
                return new Window(minute, 1);
            }
            v.count++;
            return v;
        });
        return w.count <= limitPerMinute;
    }

    private static final class Window {
        final long minute;
        int count;

        private Window(long minute, int count) {
            this.minute = minute;
            this.count = count;
        }
    }
}

