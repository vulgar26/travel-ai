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
 * 由请求体 {@code eval_tool_scenario} 驱动：{@code success|timeout|error}；未设置则不执行工具逻辑。
 */
@Component
public class EvalToolStageRunner {

    public static final String STUB_TOOL_NAME = "eval_stub_tool";

    /** 与 {@code tool.outcome} / {@code meta.tool_outcome} 一致。 */
    public static final String OUTCOME_OK = "ok";
    public static final String OUTCOME_TIMEOUT = "timeout";
    public static final String OUTCOME_ERROR = "error";

    public static final String ERROR_CODE_TOOL_TIMEOUT = "TOOL_TIMEOUT";
    public static final String ERROR_CODE_TOOL_ERROR = "TOOL_ERROR";

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
        return switch (scenario) {
            case "success" -> new EvalToolInvocationResult(true, STUB_TOOL_NAME, OUTCOME_OK, EvalToolInvocationResult.Kind.OK);
            case "timeout" -> runWithTimeout();
            case "error" -> new EvalToolInvocationResult(true, STUB_TOOL_NAME, OUTCOME_ERROR, EvalToolInvocationResult.Kind.ERROR);
            default -> null;
        };
    }

    private EvalToolInvocationResult runWithTimeout() {
        ExecutorService es = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "eval-tool-timeout-stub");
            t.setDaemon(true);
            return t;
        });
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
            return new EvalToolInvocationResult(true, STUB_TOOL_NAME, OUTCOME_OK, EvalToolInvocationResult.Kind.OK);
        } catch (TimeoutException e) {
            return new EvalToolInvocationResult(true, STUB_TOOL_NAME, OUTCOME_TIMEOUT, EvalToolInvocationResult.Kind.TIMEOUT);
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return new EvalToolInvocationResult(true, STUB_TOOL_NAME, OUTCOME_ERROR, EvalToolInvocationResult.Kind.ERROR);
        } finally {
            es.shutdownNow();
        }
    }

    /**
     * TOOL 阶段一次调用的结果（供 {@link EvalChatService} 写入 {@code tool} / {@code meta} / {@code error_code}）。
     */
    public record EvalToolInvocationResult(boolean used, String name, String outcome, Kind kind) {
        public enum Kind {
            OK,
            TIMEOUT,
            ERROR
        }
    }
}
