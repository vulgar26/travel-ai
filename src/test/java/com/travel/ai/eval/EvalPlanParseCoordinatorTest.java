package com.travel.ai.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.ai.eval.planrepair.EvalPlanParseCoordinator;
import com.travel.ai.eval.planrepair.SafePlanRepairHint;
import com.travel.ai.plan.PlanParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class EvalPlanParseCoordinatorTest {

    private final MutablePlanRepairModelPort repair = new MutablePlanRepairModelPort();
    private PlanParser planParser;
    private EvalPlanParseCoordinator coordinator;

    @BeforeEach
    void setUp() {
        repair.reset();
        planParser = new PlanParser(new ObjectMapper());
        coordinator = new EvalPlanParseCoordinator(planParser, repair);
    }

    @Test
    void defaultPlan_firstAttemptSuccess() {
        EvalPlanParseCoordinator.Result r = coordinator.parseWithOptionalRepair(null);
        assertThat(r.attempts()).isEqualTo(1);
        assertThat(r.outcome()).isEqualTo(EvalPlanParseCoordinator.OUTCOME_SUCCESS);
        assertThat(r.failed()).isFalse();
    }

    @Test
    void badPlan_repairReturnsValid_repaired() {
        repair.setRepairResponse(EvalPlanParseCoordinator.DEFAULT_EVAL_PLAN_JSON);
        EvalPlanParseCoordinator.Result r = coordinator.parseWithOptionalRepair("{\"plan_version\":\"v1\"}");
        assertThat(r.attempts()).isEqualTo(2);
        assertThat(r.outcome()).isEqualTo(EvalPlanParseCoordinator.OUTCOME_REPAIRED);
        assertThat(r.failed()).isFalse();
    }

    @Test
    void badPlan_repairStillInvalid_failed() {
        repair.setRepairResponse("{\"plan_version\":\"v1\",\"steps\":[]}");
        EvalPlanParseCoordinator.Result r = coordinator.parseWithOptionalRepair("{\"plan_version\":\"v1\"}");
        assertThat(r.attempts()).isEqualTo(2);
        assertThat(r.outcome()).isEqualTo(EvalPlanParseCoordinator.OUTCOME_FAILED);
        assertThat(r.failed()).isTrue();
    }

    @Test
    void repairHint_doesNotEchoRawPlanSecret() {
        String secret = "SECRET_RAW_TOKEN_z9k";
        AtomicReference<SafePlanRepairHint> captured = new AtomicReference<>();
        EvalPlanParseCoordinator c = new EvalPlanParseCoordinator(planParser, hint -> {
            captured.set(hint);
            return EvalPlanParseCoordinator.DEFAULT_EVAL_PLAN_JSON;
        });
        c.parseWithOptionalRepair(secret);
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().toCompactString()).doesNotContain(secret);
    }
}
