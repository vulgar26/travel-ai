package com.travel.ai.eval.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 评测响应 {@code meta}：P0 至少包含 {@code mode}；Day1 骨架额外带 {@code request_id} 便于日志串联。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvalChatMeta {

    /**
     * 本轮模式：如 {@code EVAL}、{@code AGENT}；若请求体未传 {@code mode}，默认 {@code EVAL}。
     */
    private String mode;

    /** 单次请求唯一 id（UUID），便于与网关/日志 trace 对齐。 */
    private String requestId;

    public EvalChatMeta() {
    }

    public EvalChatMeta(String mode, String requestId) {
        this.mode = mode;
        this.requestId = requestId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
