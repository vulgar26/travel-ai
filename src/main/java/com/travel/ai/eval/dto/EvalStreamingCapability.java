package com.travel.ai.eval.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * {@code capabilities.streaming}：非流式评测接口下 {@code ttft} 应为 false（与 {@code plans/eval-upgrade.md} 一致）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvalStreamingCapability {

    private boolean ttft;

    public EvalStreamingCapability() {
    }

    public EvalStreamingCapability(boolean ttft) {
        this.ttft = ttft;
    }

    public boolean isTtft() {
        return ttft;
    }

    public void setTtft(boolean ttft) {
        this.ttft = ttft;
    }
}
