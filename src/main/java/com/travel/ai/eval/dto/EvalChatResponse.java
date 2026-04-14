package com.travel.ai.eval.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 评测专用 {@code POST /api/v1/eval/chat} 的响应体（P0 必填顶层字段，对外 snake_case）。
 * <p>
 * 必填：{@code answer}、{@code behavior}、{@code latency_ms}、{@code capabilities}、{@code meta}。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    /** 如 {@code PARSE_ERROR}；仅在有归因时返回。 */
    private String errorCode;

    /**
     * 引用证据（服务端生成）：{@code {id,title,snippet,score?}}。
     * <p>
     * 在 {@code requires_citations=true} 的 case 上，eval 会强制要求该字段非空（规则见 eval 侧）。
     */
    private java.util.List<EvalChatSource> sources;

    /**
     * 根级候选 hits（membership 校验用；至少含 {@code id}）。
     * <p>
     * P0+ 推荐对齐：只输出前 N（由 eval 下发的 top_n 决定），且 {@code sources[*].id} 必须为其子集。
     */
    private java.util.List<EvalChatRetrievalHit> retrievalHits;

    /** 工具归因（{@code used} / {@code outcome}）；未走评测工具场景时不返回。 */
    private EvalChatResultTool tool;

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

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public java.util.List<EvalChatSource> getSources() {
        return sources;
    }

    public void setSources(java.util.List<EvalChatSource> sources) {
        this.sources = sources;
    }

    public java.util.List<EvalChatRetrievalHit> getRetrievalHits() {
        return retrievalHits;
    }

    public void setRetrievalHits(java.util.List<EvalChatRetrievalHit> retrievalHits) {
        this.retrievalHits = retrievalHits;
    }

    public EvalChatResultTool getTool() {
        return tool;
    }

    public void setTool(EvalChatResultTool tool) {
        this.tool = tool;
    }
}
