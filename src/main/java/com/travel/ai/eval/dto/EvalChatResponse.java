package com.travel.ai.eval.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 评测专用 {@code POST /api/v1/eval/chat} 的响应体（P0 必填顶层字段，对外 snake_case）。
 * <p>
 * 必填：{@code answer}、{@code behavior}、{@code latency_ms}、{@code capabilities}、{@code meta}。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvalChatResponse {

    private String answer;

    /** {@code answer|clarify|deny|tool}，与 {@code plans/eval-upgrade.md} 一致。 */
    private String behavior;

    /** 服务端测量的总耗时（毫秒），与 HTTP 非流式一次返回对齐。 */
    private long latencyMs;

    private EvalCapabilities capabilities;

    /** 至少含 {@code mode}（见 {@link EvalChatMeta}）。 */
    private EvalChatMeta meta;

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getBehavior() {
        return behavior;
    }

    public void setBehavior(String behavior) {
        this.behavior = behavior;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public EvalCapabilities getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(EvalCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    public EvalChatMeta getMeta() {
        return meta;
    }

    public void setMeta(EvalChatMeta meta) {
        this.meta = meta;
    }
}
