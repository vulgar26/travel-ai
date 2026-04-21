package com.travel.ai.controller.dto;

/**
 * {@code POST /travel/chat/{conversationId}} 请求体：将用户问题放在 JSON 中，避免 GET 查询串长度与日志暴露问题。
 */
public class TravelChatRequest {

    private String query;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
