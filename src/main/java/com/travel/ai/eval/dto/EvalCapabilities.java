package com.travel.ai.eval.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 评测响应中的 {@code capabilities}：表示<strong>本次请求下实际生效的能力</strong>（effective capabilities），
 * 不是「系统理论上可能支持的全部能力」。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvalCapabilities {

    private EvalRetrievalCapability retrieval;
    private EvalToolsCapability tools;
    private EvalStreamingCapability streaming;
    private EvalGuardrailsCapability guardrails;

    public EvalCapabilities() {
    }

    public EvalCapabilities(
            EvalRetrievalCapability retrieval,
            EvalToolsCapability tools,
            EvalStreamingCapability streaming,
            EvalGuardrailsCapability guardrails) {
        this.retrieval = retrieval;
        this.tools = tools;
        this.streaming = streaming;
        this.guardrails = guardrails;
    }

    public EvalRetrievalCapability getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(EvalRetrievalCapability retrieval) {
        this.retrieval = retrieval;
    }

    public EvalToolsCapability getTools() {
        return tools;
    }

    public void setTools(EvalToolsCapability tools) {
        this.tools = tools;
    }

    public EvalStreamingCapability getStreaming() {
        return streaming;
    }

    public void setStreaming(EvalStreamingCapability streaming) {
        this.streaming = streaming;
    }

    public EvalGuardrailsCapability getGuardrails() {
        return guardrails;
    }

    public void setGuardrails(EvalGuardrailsCapability guardrails) {
        this.guardrails = guardrails;
    }
}
