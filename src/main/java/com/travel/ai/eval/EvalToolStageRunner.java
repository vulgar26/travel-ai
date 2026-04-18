package com.travel.ai.eval;

import com.travel.ai.eval.dto.EvalChatRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Day6：在 TOOL 阶段<strong>串行</strong>执行至多一次评测用 stub 调用（不并行多工具），带超时与失败降级语义。
 * <p>
 * 由请求体 {@code eval_tool_scenario} 驱动：{@code success|success_truncated|timeout|error|circuit_breaker|rate_limited}；未设置则不执行工具逻辑。
 *
 * 这里的“熔断/限流/截断”场景是为了把 meta 字段跑通（可回归、可分桶），不依赖真实外部工具。
 */
@Component
public class EvalToolStageRunner {

    public static final String STUB_TOOL_NAME = "eval_stub_tool";

    /** 与 {@code tool.outcome} / {@code meta.tool_outcome} 一致。 */
    public static final String OUTCOME_OK = "ok";
    public static final String OUTCOME_TIMEOUT = "timeout";
    public static final String OUTCOME_ERROR = "error";
    public static final String OUTCOME_DISABLED_BY_CIRCUIT_BREAKER = "disabled_by_circuit_breaker";
    public static final String OUTCOME_RATE_LIMITED = "rate_limited";

    public static final String ERROR_CODE_TOOL_TIMEOUT = "TOOL_TIMEOUT";
    public static final String ERROR_CODE_TOOL_ERROR = "TOOL_ERROR";
    public static final String ERROR_CODE_TOOL_DISABLED_BY_CIRCUIT_BREAKER = "TOOL_DISABLED_BY_CIRCUIT_BREAKER";
    public static final String ERROR_CODE_RATE_LIMITED = "RATE_LIMITED";

    private final long toolTimeoutMs;

    public EvalToolStageRunner(@Value("${app.eval.tool-timeout-ms:100}") long toolTimeoutMs) {
        this.toolTimeoutMs = Math.max(20L, toolTimeoutMs);
    }

    /**
     * @return {@code null} 表示本请求不跑评测工具场景
     */
    public EvalToolInvocationResult invoke(EvalChatRequest request) {
        String raw = request.getEvalToolScenario();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String scenario = raw.trim().toLowerCase(Locale.ROOT);
        long start = System.nanoTime();
        EvalToolInvocationResult r = switch (scenario) {
            case "success" -> new EvalToolInvocationResult(true, STUB_TOOL_NAME, OUTCOME_OK, EvalToolInvocationResult.Kind.OK, 0L);
            case "success_truncated" -> new EvalToolInvocationResult(
                    true, STUB_TOOL_NAME, OUTCOME_OK, EvalToolInvocationResult.Kind.OK, 0L,
                    null, null, true);
            case "timeout" -> runWithTimeout();
            case "error" -> new EvalToolInvocationResult(true, STUB_TOOL_NAME, OUTCOME_ERROR, EvalToolInvocationResult.Kind.ERROR, 0L);
            case "circuit_breaker" -> new EvalToolInvocationResult(
                    false, STUB_TOOL_NAME, OUTCOME_DISABLED_BY_CIRCUIT_BREAKER, EvalToolInvocationResult.Kind.CIRCUIT_BREAKER, 0L,
                    true, null, null);
            case "rate_limited" -> new EvalToolInvocationResult(
                    false, STUB_TOOL_NAME, OUTCOME_RATE_LIMITED, EvalToolInvocationResult.Kind.RATE_LIMITED, 0L,
                    null, true, null);
            default -> null;
        };
        if (r == null) {
            return null;
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        return new EvalToolInvocationResult(
                r.used(), r.name(), r.outcome(), r.kind(), elapsedMs,
                r.toolDisabledByCircuitBreaker(), r.toolRateLimited(), r.toolOutputTruncated());
    }

    private EvalToolInvocationResult runWithTimeout() {
        ExecutorService es = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "eval-tool-timeout-stub");
            t.setDaemon(true);
            return t;
        });
        long start = System.nanoTime();
        try {
            Future<?> f = es.submit(() -> {
                try {
                    Thread.sleep(toolTimeoutMs + 500L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
            f.get(toolTimeoutMs, TimeUnit.MILLISECONDS);
            long ms = (System.nanoTime() - start) / 1_000_000L;
            return new EvalToolInvocationResult(true, STUB_TOOL_NAME, OUTCOME_OK, EvalToolInvocationResult.Kind.OK, ms);
        } catch (TimeoutException e) {
            long ms = (System.nanoTime() - start) / 1_000_000L;
            return new EvalToolInvocationResult(true, STUB_TOOL_NAME, OUTCOME_TIMEOUT, EvalToolInvocationResult.Kind.TIMEOUT, ms);
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            long ms = (System.nanoTime() - start) / 1_000_000L;
            return new EvalToolInvocationResult(true, STUB_TOOL_NAME, OUTCOME_ERROR, EvalToolInvocationResult.Kind.ERROR, ms);
        } finally {
            es.shutdownNow();
        }
    }

    /**
     * TOOL 阶段一次调用的结果（供 {@link EvalChatService} 写入 {@code tool} / {@code meta} / {@code error_code}）。
     */
    public record EvalToolInvocationResult(
            boolean used,
            String name,
            String outcome,
            Kind kind,
            long latencyMs,
            Boolean toolDisabledByCircuitBreaker,
            Boolean toolRateLimited,
            Boolean toolOutputTruncated
    ) {
        public EvalToolInvocationResult(boolean used, String name, String outcome, Kind kind, long latencyMs) {
            this(used, name, outcome, kind, latencyMs, null, null, null);
        }

        public enum Kind {
            OK,
            TIMEOUT,
            ERROR,
            CIRCUIT_BREAKER,
            RATE_LIMITED
        }
    }
}
