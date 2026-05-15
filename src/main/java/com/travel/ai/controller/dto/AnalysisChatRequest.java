package com.travel.ai.controller.dto;

/**
 * {@code POST /analysis/chat/{conversationId}} 请求体。
 * <p>
 * 与 legacy {@link TravelChatRequest} 保持同一 JSON 契约：只包含 {@code query}。
 */
public class AnalysisChatRequest {

    private String query;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}

