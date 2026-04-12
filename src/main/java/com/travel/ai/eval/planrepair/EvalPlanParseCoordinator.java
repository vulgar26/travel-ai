package com.travel.ai.eval.planrepair;

import com.travel.ai.plan.PlanParseException;
import com.travel.ai.plan.PlanParser;
import org.springframework.stereotype.Component;

/**
 * Day5：对 eval 侧 plan 文本 <strong>parse → 失败则 repair 一次 → 再 parse</strong>，绝不第三次尝试。
 */
@Component
public class EvalPlanParseCoordinator {

    /**
     * 非空 query 且未传 {@code plan_raw} 时使用的默认合法 plan（单步 WRITE，满足附录 E 骨架）。
     */
    public static final String DEFAULT_EVAL_PLAN_JSON = """
            {
              "plan_version": "v1",
              "steps": [
                { "step_id": "s1", "stage": "WRITE", "instruction": "输出评测占位答复" }
              ],
              "constraints": { "max_steps": 1, "total_timeout_ms": 60000, "tool_timeout_ms": 10000 }
            }
            """;

    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_REPAIRED = "repaired";
    public static final String OUTCOME_FAILED = "failed";

    private final PlanParser planParser;
    private final PlanRepairModelPort repairModelPort;

    public EvalPlanParseCoordinator(PlanParser planParser, PlanRepairModelPort repairModelPort) {
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
            return new Result(1, OUTCOME_SUCCESS, null);
        } catch (PlanParseException first) {
            SafePlanRepairHint hint = SafePlanRepairHintBuilder.from(first);
            String repairedRaw = repairModelPort.repair(hint);
            try {
                planParser.parse(repairedRaw);
                return new Result(2, OUTCOME_REPAIRED, null);
            } catch (PlanParseException second) {
                return new Result(2, OUTCOME_FAILED, second);
            }
        }
    }

    public record Result(int attempts, String outcome, PlanParseException lastFailure) {

        public boolean failed() {
            return OUTCOME_FAILED.equals(outcome);
        }
    }
}
