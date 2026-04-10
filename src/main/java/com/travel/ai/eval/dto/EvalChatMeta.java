package com.travel.ai.eval.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * 评测响应 {@code meta}：P0 至少包含 {@code mode}；另含 travel-ai 可控性字段（见 {@code plans/p0-execution-map.md} 附录 C3）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvalChatMeta {

    /**
     * P0 硬指标：禁止动态 replan 循环，对外 {@code replan_count} 必须恒为该值（eval 可回归统计）。
     */
    public static final int P0_REPLAN_COUNT = 0;

    /**
     * 本轮模式：如 {@code EVAL}、{@code AGENT}；若请求体未传 {@code mode}，默认 {@code EVAL}。
     */
    private String mode;

    /** 单次请求唯一 id（UUID），便于与网关/日志 trace 对齐。 */
    private String requestId;

    /**
     * 本请求实际经过的线性阶段序列（大写），为 {@code PLAN|RETRIEVE|TOOL|WRITE|GUARD} 的子序列。
     * 空 query 等未跑流水线时为 {@code []}。
     */
    private List<String> stageOrder;

    /** 实际执行过的阶段数；应与 {@code stage_order.length} 一致（串行计数）。 */
    private int stepCount;

    /** P0 禁止 replan 循环，恒为 {@code 0}。 */
    private int replanCount;

    public EvalChatMeta() {
    }

    public EvalChatMeta(String mode, String requestId) {
        this.mode = mode;
        this.requestId = requestId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public List<String> getStageOrder() {
        return stageOrder;
    }

    public void setStageOrder(List<String> stageOrder) {
        this.stageOrder = stageOrder;
    }

    public int getStepCount() {
        return stepCount;
    }

    public void setStepCount(int stepCount) {
        this.stepCount = stepCount;
    }

    public int getReplanCount() {
        return replanCount;
    }

    public void setReplanCount(int replanCount) {
        this.replanCount = replanCount;
    }
}
