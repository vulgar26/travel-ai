package com.travel.ai.eval.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * {@code capabilities.tools}：工具能力是否在本次请求中生效，以及是否返回 {@code tool.outcome} 等字段。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvalToolsCapability {

    private boolean supported;
    private boolean outcome;

    public EvalToolsCapability() {
    }

    public EvalToolsCapability(boolean supported, boolean outcome) {
        this.supported = supported;
        this.outcome = outcome;
    }

    public boolean isSupported() {
        return supported;
    }

    public void setSupported(boolean supported) {
        this.supported = supported;
    }

    public boolean isOutcome() {
        return outcome;
    }

    public void setOutcome(boolean outcome) {
        this.outcome = outcome;
    }
}
