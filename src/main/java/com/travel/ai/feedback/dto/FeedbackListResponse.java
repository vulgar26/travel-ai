package com.travel.ai.feedback.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class FeedbackListResponse {

    private List<FeedbackItemResponse> items;

    public FeedbackListResponse(List<FeedbackItemResponse> items) {
        this.items = items;
    }

    public List<FeedbackItemResponse> getItems() {
        return items;
    }
}
