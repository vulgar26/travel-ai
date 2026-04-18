package com.travel.ai.tools;

import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.function.Supplier;

public final class ToolExecutor {

    public static final String ERROR_CODE_TOOL_TIMEOUT = "TOOL_TIMEOUT";
    public static final String ERROR_CODE_TOOL_ERROR = "TOOL_ERROR";
    public static final String ERROR_CODE_POLICY_DISABLED = "TOOL_POLICY_DISABLED";

    private ToolExecutor() {
    }

    public static ToolResult execute(
            String toolName,
            boolean required,
            boolean enabledByPolicy,
            int maxSummaryChars,
            Supplier<String> call
    ) {
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(call, "call");

        if (!enabledByPolicy) {
            return ToolResult.disabledByPolicy(toolName, required, ERROR_CODE_POLICY_DISABLED);
        }

        long start = System.nanoTime();
        try {
            // 约束：工具原始输出（raw）不直接进入日志/持久化/上游 API。
            // 这里只产出“摘要（summary）+ 是否截断”，供 prompt 注入与统计。
            String raw = call.get();
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            Summary s = summarize(raw, Math.max(0, maxSummaryChars));
            return new ToolResult(
                    toolName,
                    required,
                    true,
                    true,
                    ToolOutcome.OK,
                    null,
                    latencyMs,
                    s.text,
                    s.truncated
            );
        } catch (RuntimeException e) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            Throwable root = rootCause(e);
            // 口径：把“工具超时”与“其他异常”分桶到不同 outcome + error_code，便于回归统计。
            if (root instanceof SocketTimeoutException) {
                return new ToolResult(
                        toolName,
                        required,
                        true,
                        false,
                        ToolOutcome.TIMEOUT,
                        ERROR_CODE_TOOL_TIMEOUT,
                        latencyMs,
                        null,
                        false
                );
            }
            return new ToolResult(
                    toolName,
                    required,
                    true,
                    false,
                    ToolOutcome.ERROR,
                    ERROR_CODE_TOOL_ERROR,
                    latencyMs,
                    null,
                    false
            );
        }
    }

    private static Summary summarize(String raw, int maxChars) {
        if (raw == null) {
            return new Summary("", false);
        }
        String t = raw.trim();
        if (maxChars <= 0 || t.length() <= maxChars) {
            return new Summary(t, false);
        }
        return new Summary(t.substring(0, maxChars) + "…", true);
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    private record Summary(String text, boolean truncated) {
    }
}

