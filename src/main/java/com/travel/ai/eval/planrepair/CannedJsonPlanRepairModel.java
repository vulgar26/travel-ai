package com.travel.ai.eval.planrepair;

import com.travel.ai.plan.PlanParseCoordinator;
import com.travel.ai.plan.PlanRepairModelPort;
import com.travel.ai.plan.SafePlanRepairHint;
import org.springframework.stereotype.Component;

/**
 * Day5 默认修复：忽略 hint，返回与 {@link PlanParseCoordinator#DEFAULT_EVAL_PLAN_JSON} 一致的合法 plan，保证 repair 路径可解析。
 */
@Component
public class CannedJsonPlanRepairModel implements PlanRepairModelPort {

    @Override
    public String repair(SafePlanRepairHint hint) {
        return PlanParseCoordinator.DEFAULT_EVAL_PLAN_JSON;
    }
}
