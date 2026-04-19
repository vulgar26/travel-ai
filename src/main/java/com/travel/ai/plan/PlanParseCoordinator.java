package com.travel.ai.plan;

import org.springframework.stereotype.Component;

/**
 * 对 plan 文本 <strong>parse → 失败则 repair 一次 → 再 parse</strong>，绝不第三次尝试。
 * <p>
 * 主线与评测共用（原 {@code EvalPlanParseCoordinator}，已迁至中性包 {@code com.travel.ai.plan}）。
 */
@Component
public class PlanParseCoordinator {

    /**
     * 非空 query 且未传 {@code plan_raw} 时使用的默认合法 plan（单步 WRITE，满足附录 E 骨架）。
     */
    public static final String DEFAULT_EVAL_PLAN_JSON = """
            {
              "plan_version": "v1",
              "goal": "评测默认全阶段占位",
              "steps": [
                { "step_id": "s1", "stage": "RETRIEVE", "instruction": "检索相关知识片段。" },
                { "step_id": "s2", "stage": "TOOL", "instruction": "按需调用受控工具。" },
                { "step_id": "s3", "stage": "GUARD", "instruction": "门控与证据检查。" },
                { "step_id": "s4", "stage": "WRITE", "instruction": "生成评测占位答复。" }
              ],
              "constraints": { "max_steps": 8, "total_timeout_ms": 60000, "tool_timeout_ms": 10000 }
            }
            """;

    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_REPAIRED = "repaired";
    public static final String OUTCOME_FAILED = "failed";

    private final PlanParser planParser;
    private final PlanRepairModelPort repairModelPort;

    public PlanParseCoordinator(PlanParser planParser, PlanRepairModelPort repairModelPort) {
        this.planParser = planParser;
        this.repairModelPort = repairModelPort;
    }

    /**
     * @param planRaw 可选；空则使用 {@link #DEFAULT_EVAL_PLAN_JSON}
     * @return 尝试次数与结果；成功为 1 次或 repair 后 2 次
     */
    public Result parseWithOptionalRepair(String planRaw) {
        String raw = (planRaw == null || planRaw.isBlank()) ? DEFAULT_EVAL_PLAN_JSON : planRaw.trim();
        try {
            planParser.parse(raw);
            return new Result(1, OUTCOME_SUCCESS, null, raw);
        } catch (PlanParseException first) {
            SafePlanRepairHint hint = SafePlanRepairHintBuilder.from(first);
            String repairedRaw = repairModelPort.repair(hint);
            try {
                planParser.parse(repairedRaw);
                return new Result(2, OUTCOME_REPAIRED, null, repairedRaw);
            } catch (PlanParseException second) {
                return new Result(2, OUTCOME_FAILED, second, null);
            }
        }
    }

    /**
     * @param effectivePlanJson 与 {@link #outcome()} 对应的成功 plan 原文；{@link #failed()} 时为 {@code null}
     */
    public record Result(int attempts, String outcome, PlanParseException lastFailure, String effectivePlanJson) {

        public boolean failed() {
            return OUTCOME_FAILED.equals(outcome);
        }
    }
}
