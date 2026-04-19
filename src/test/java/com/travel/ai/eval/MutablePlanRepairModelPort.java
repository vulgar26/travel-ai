package com.travel.ai.eval;

import com.travel.ai.plan.PlanParseCoordinator;
import com.travel.ai.plan.PlanRepairModelPort;
import com.travel.ai.plan.SafePlanRepairHint;

/**
 * 无 Mockito：供 {@link EvalChatControllerTest} 等在 JDK 25+ 下稳定替换 repair 返回值。
 */
public class MutablePlanRepairModelPort implements PlanRepairModelPort {

    private volatile String repairResponse = PlanParseCoordinator.DEFAULT_EVAL_PLAN_JSON;

    public void setRepairResponse(String json) {
        this.repairResponse = json;
    }

    public void reset() {
        this.repairResponse = PlanParseCoordinator.DEFAULT_EVAL_PLAN_JSON;
    }

    @Override
    public String repair(SafePlanRepairHint hint) {
        return repairResponse;
    }
}
