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
}
