package com.travel.ai.eval.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * 评测响应 {@code meta}：P0 至少包含 {@code mode}；另含 travel-ai 可控性字段（见 {@code plans/p0-execution-map.md} 附录 C3）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    /**
     * Plan 解析尝试次数：{@code 1} 首次成功；{@code 2} 首次失败后经过一次 repair 再解析。
     * 未执行 plan 解析（如空 query）时不返回该字段。
     */
    private Integer planParseAttempts;

    /**
     * {@code success|repaired|failed}，见 {@code plans/travel-ai-upgrade.md} P0 Plan 解析治理。
     */
    private String planParseOutcome;

    /**
     * 本请求工具调用次数（P0 串行，Day6 stub 为 0 或 1）；未走评测工具场景时不返回。
     */
    private Integer toolCallsCount;

    /** {@code ok|timeout|error}，与顶层 {@code tool.outcome} 一致；未走工具场景时不返回。 */
    private String toolOutcome;

    /**
     * 是否处于低置信/空命中等门控（P0 不启用 score 阈值时仍可由 stub 置 true 供 eval 统计）。
     */
    private Boolean lowConfidence;

    /**
     * 门控原因枚举式说明（非空时可回归）；未触发门控时不返回。
     */
    private List<String> reasons;

    /**
     * 仅在 SafetyGate 规则短路时写入，用于报表分桶与归因。
     * <p>
     * 约束：非短路路径必须不返回该字段（避免污染统计口径）。
     */
    private String evalSafetyRuleId;

    /**
     * 检索命中条数（评测 stub）；空命中场景为 0；未参与门控时不返回。
     */
    private Integer retrieveHitCount;

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

    public Integer getPlanParseAttempts() {
        return planParseAttempts;
    }

    public void setPlanParseAttempts(Integer planParseAttempts) {
        this.planParseAttempts = planParseAttempts;
    }

    public String getPlanParseOutcome() {
        return planParseOutcome;
    }

    public void setPlanParseOutcome(String planParseOutcome) {
        this.planParseOutcome = planParseOutcome;
    }

    public Integer getToolCallsCount() {
        return toolCallsCount;
    }

    public void setToolCallsCount(Integer toolCallsCount) {
        this.toolCallsCount = toolCallsCount;
    }

    public String getToolOutcome() {
        return toolOutcome;
    }

    public void setToolOutcome(String toolOutcome) {
        this.toolOutcome = toolOutcome;
    }

    public Boolean getLowConfidence() {
        return lowConfidence;
    }

    public void setLowConfidence(Boolean lowConfidence) {
        this.lowConfidence = lowConfidence;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }

    public String getEvalSafetyRuleId() {
        return evalSafetyRuleId;
    }

    public void setEvalSafetyRuleId(String evalSafetyRuleId) {
        this.evalSafetyRuleId = evalSafetyRuleId;
    }

    public Integer getRetrieveHitCount() {
        return retrieveHitCount;
    }

    public void setRetrieveHitCount(Integer retrieveHitCount) {
        this.retrieveHitCount = retrieveHitCount;
    }
}
