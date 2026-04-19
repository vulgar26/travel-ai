package com.travel.ai.eval.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * 评测响应 {@code meta}：P0 至少包含 {@code mode}；另含 travel-ai 可控性字段（见 {@code plans/p0-execution-map.md} 附录 C3）。
 * <p>
 * 联调说明：上游 eval 平台会将本对象整段快照落库为 {@code eval_result.target_meta_json}（受体积与安全策略约束），
 * compare 摘要中的 {@code *_meta_trace} 仅包含各 target 在运维侧配置的 {@code eval.targets[].meta-trace-keys} 所列键；
 * 未配置的键不会出现在 compare trace 中（全量仍以导出接口中的 {@code meta} 为准）。
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

    /**
     * {@code ok|timeout|error|disabled_by_circuit_breaker|rate_limited|…}，与顶层 {@code tool.outcome} 一致；
     * 未走工具场景时不返回。
     */
    private String toolOutcome;

    /** 工具调用耗时（ms）；未走工具场景时不返回。 */
    private Long toolLatencyMs;

    /** 工具是否因熔断被禁用；未走工具场景或未启用熔断时不返回。 */
    private Boolean toolDisabledByCircuitBreaker;

    /** 工具是否因限流被拒绝；未走工具场景或未启用限流时不返回。 */
    private Boolean toolRateLimited;

    /** 工具输出是否被截断/摘要；未走工具场景时不返回。 */
    private Boolean toolOutputTruncated;

    /**
     * 是否处于低置信/空命中等门控（P0 不启用 score 阈值时仍可由 stub 置 true 供 eval 统计）。
     */
    private Boolean lowConfidence;

    /**
     * 低置信 / 门控原因列表（与 Vagent 常见字段 {@code low_confidence_reasons} 对齐，JSON snake_case）。
     */
    private List<String> lowConfidenceReasons;

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

    /**
     * eval-upgrade.md E7：候选 hitId 的 HMAC-SHA256 证据（64 位小写 hex）；须非空数组才视为携带证据。
     */
    private List<String> retrievalHitIdHashes;

    /** 与 {@link #retrievalHitIdHashes} 同时出现；固定为 {@code HMAC-SHA256}。 */
    private String retrievalHitIdHashAlg;

    /** 与 {@link #retrievalHitIdHashes} 同时出现；固定为 {@code x-eval-token/v1}。 */
    private String retrievalHitIdHashKeyDerivation;

    /** 与 {@link #retrievalHitIdHashes} 同时出现：候选截断上限 N。 */
    private Integer retrievalCandidateLimitN;

    /** 与 {@link #retrievalHitIdHashes} 同时出现：去重后的候选总数（截断前）。 */
    private Integer retrievalCandidateTotal;

    /** 与 {@link #retrievalHitIdHashes} 同时出现：本 target 使用的 canonical id 口径。 */
    private String canonicalHitIdScheme;

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

    public Long getToolLatencyMs() {
        return toolLatencyMs;
    }

    public void setToolLatencyMs(Long toolLatencyMs) {
        this.toolLatencyMs = toolLatencyMs;
    }

    public Boolean getToolDisabledByCircuitBreaker() {
        return toolDisabledByCircuitBreaker;
    }

    public void setToolDisabledByCircuitBreaker(Boolean toolDisabledByCircuitBreaker) {
        this.toolDisabledByCircuitBreaker = toolDisabledByCircuitBreaker;
    }

    public Boolean getToolRateLimited() {
        return toolRateLimited;
    }

    public void setToolRateLimited(Boolean toolRateLimited) {
        this.toolRateLimited = toolRateLimited;
    }

    public Boolean getToolOutputTruncated() {
        return toolOutputTruncated;
    }

    public void setToolOutputTruncated(Boolean toolOutputTruncated) {
        this.toolOutputTruncated = toolOutputTruncated;
    }

    public Boolean getLowConfidence() {
        return lowConfidence;
    }

    public void setLowConfidence(Boolean lowConfidence) {
        this.lowConfidence = lowConfidence;
    }

    public List<String> getLowConfidenceReasons() {
        return lowConfidenceReasons;
    }

    public void setLowConfidenceReasons(List<String> lowConfidenceReasons) {
        this.lowConfidenceReasons = lowConfidenceReasons;
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

    public List<String> getRetrievalHitIdHashes() {
        return retrievalHitIdHashes;
    }

    public void setRetrievalHitIdHashes(List<String> retrievalHitIdHashes) {
        this.retrievalHitIdHashes = retrievalHitIdHashes;
    }

    public String getRetrievalHitIdHashAlg() {
        return retrievalHitIdHashAlg;
    }

    public void setRetrievalHitIdHashAlg(String retrievalHitIdHashAlg) {
        this.retrievalHitIdHashAlg = retrievalHitIdHashAlg;
    }

    public String getRetrievalHitIdHashKeyDerivation() {
        return retrievalHitIdHashKeyDerivation;
    }

    public void setRetrievalHitIdHashKeyDerivation(String retrievalHitIdHashKeyDerivation) {
        this.retrievalHitIdHashKeyDerivation = retrievalHitIdHashKeyDerivation;
    }

    public Integer getRetrievalCandidateLimitN() {
        return retrievalCandidateLimitN;
    }

    public void setRetrievalCandidateLimitN(Integer retrievalCandidateLimitN) {
        this.retrievalCandidateLimitN = retrievalCandidateLimitN;
    }

    public Integer getRetrievalCandidateTotal() {
        return retrievalCandidateTotal;
    }

    public void setRetrievalCandidateTotal(Integer retrievalCandidateTotal) {
        this.retrievalCandidateTotal = retrievalCandidateTotal;
    }

    public String getCanonicalHitIdScheme() {
        return canonicalHitIdScheme;
    }

    public void setCanonicalHitIdScheme(String canonicalHitIdScheme) {
        this.canonicalHitIdScheme = canonicalHitIdScheme;
    }
}
