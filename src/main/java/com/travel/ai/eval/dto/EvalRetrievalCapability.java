package com.travel.ai.eval.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * {@code capabilities.retrieval}：检索能力是否在本次请求中生效，以及是否提供 score。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvalRetrievalCapability {

    private boolean supported;
    private boolean score;

    public EvalRetrievalCapability() {
    }

    public EvalRetrievalCapability(boolean supported, boolean score) {
        this.supported = supported;
        this.score = score;
    }

    public boolean isSupported() {
        return supported;
    }

    public void setSupported(boolean supported) {
        this.supported = supported;
    }

    public boolean isScore() {
        return score;
    }

    public void setScore(boolean score) {
        this.score = score;
    }
}
