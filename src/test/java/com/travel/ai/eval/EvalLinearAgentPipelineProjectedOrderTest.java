package com.travel.ai.eval;

import com.travel.ai.plan.PlanPhysicalStagePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvalLinearAgentPipelineProjectedOrderTest {

    @Test
    void projectedFullStageOrder_matchesAllStagesEnabled() {
        var flags = PlanPhysicalStagePolicy.PhysicalStageFlags.allStagesAfterPlan();
        assertEquals(
                List.of("PLAN", "RETRIEVE", "TOOL", "GUARD", "WRITE"),
                EvalLinearAgentPipeline.projectedFullStageOrder(flags)
        );
    }
}
