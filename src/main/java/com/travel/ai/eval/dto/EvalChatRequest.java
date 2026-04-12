package com.travel.ai.eval.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 评测专用 {@code POST /api/v1/eval/chat} 的请求体（P0 契约，对外 snake_case）。
 * <p>
 * 字段说明：
 * <ul>
 *   <li>{@code query}：本 case 的用户问题（必填）。</li>
 *   <li>{@code mode}：可选策略开关，例如 {@code AGENT} / {@code BASELINE}；Day1 骨架仅回显到 {@code meta.mode}。</li>
 *   <li>{@code conversation_id}：可选会话 id；Day1 骨架仅占位，后续多轮/记忆接入时使用。</li>
 *   <li>{@code plan_raw}：可选，评测用 plan JSON 原文；不传则使用内置合法默认 plan 做解析观测（Day5）。</li>
 *   <li>{@code eval_tool_scenario}：可选，仅评测用；{@code success|timeout|error} 在 TOOL 阶段触发 stub（Day6）。</li>
 *   <li>{@code eval_rag_scenario}：可选，仅评测用；{@code empty|empty_hits|low_conf|low_confidence} 触发 RAG 门控 stub（Day7）。</li>
 * </ul>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvalChatRequest {

    /** 本 case 问题文本（必填）。 */
    private String query;

    /** 可选：AGENT / BASELINE / RAG 等，由评测集或调用方指定。 */
    private String mode;

    /** 可选：会话 id，P0 默认每 case 独立会话时可由 eval 传入。 */
    private String conversationId;

    /**
     * 可选：附录 E 风格 plan JSON 字符串；用于触发 {@code plan_parse_attempts/outcome} 与 repair 路径（评测专用）。
     */
    private String planRaw;

    /** 可选：{@code success|timeout|error}，触发 TOOL 阶段串行 stub 与降级矩阵（评测专用）。 */
    private String evalToolScenario;

    /** 可选：{@code empty|low_conf|…}，触发 {@code meta.low_confidence} 与 {@code reasons[]}（Day7）。 */
    private String evalRagScenario;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getPlanRaw() {
        return planRaw;
    }

    public void setPlanRaw(String planRaw) {
        this.planRaw = planRaw;
    }

    public String getEvalToolScenario() {
        return evalToolScenario;
    }

    public void setEvalToolScenario(String evalToolScenario) {
        this.evalToolScenario = evalToolScenario;
    }

    public String getEvalRagScenario() {
        return evalRagScenario;
    }

    public void setEvalRagScenario(String evalRagScenario) {
        this.evalRagScenario = evalRagScenario;
    }
}
