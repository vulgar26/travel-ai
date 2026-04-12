package com.travel.ai.eval.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 评测响应顶层 {@code tool}：与 {@code plans/travel-ai-upgrade.md} P0 工具归因一致（{@code used}、{@code outcome}）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvalChatResultTool {

    /** 本请求是否实际进入工具调用并成功发起（stub 也算一次调用）。 */
    private boolean used;

    /** 工具标识（评测 stub 固定名即可）。 */
    private String name;

    /** {@code ok|timeout|error}，与 {@code meta.tool_outcome} 对齐。 */
    private String outcome;

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }
}
