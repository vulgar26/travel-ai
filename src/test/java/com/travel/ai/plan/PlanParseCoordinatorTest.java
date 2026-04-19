package com.travel.ai.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.ai.eval.MutablePlanRepairModelPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PlanParseCoordinatorTest {

    private final MutablePlanRepairModelPort repair = new MutablePlanRepairModelPort();
    private PlanParser planParser;
    private PlanParseCoordinator coordinator;

    @BeforeEach
    void setUp() {
        repair.reset();
        planParser = new PlanParser(new ObjectMapper());
        coordinator = new PlanParseCoordinator(planParser, repair);
    }

    @Test
    void defaultPlan_firstAttemptSuccess() {
        PlanParseCoordinator.Result r = coordinator.parseWithOptionalRepair(null);
        assertThat(r.attempts()).isEqualTo(1);
        assertThat(r.outcome()).isEqualTo(PlanParseCoordinator.OUTCOME_SUCCESS);
        assertThat(r.failed()).isFalse();
    }

    @Test
    void badPlan_repairReturnsValid_repaired() {
        repair.setRepairResponse(PlanParseCoordinator.DEFAULT_EVAL_PLAN_JSON);
        PlanParseCoordinator.Result r = coordinator.parseWithOptionalRepair("{\"plan_version\":\"v1\"}");
        assertThat(r.attempts()).isEqualTo(2);
        assertThat(r.outcome()).isEqualTo(PlanParseCoordinator.OUTCOME_REPAIRED);
        assertThat(r.failed()).isFalse();
    }

    @Test
    void badPlan_repairStillInvalid_failed() {
        repair.setRepairResponse("{\"plan_version\":\"v1\",\"steps\":[]}");
        PlanParseCoordinator.Result r = coordinator.parseWithOptionalRepair("{\"plan_version\":\"v1\"}");
        assertThat(r.attempts()).isEqualTo(2);
        assertThat(r.outcome()).isEqualTo(PlanParseCoordinator.OUTCOME_FAILED);
        assertThat(r.failed()).isTrue();
    }

    @Test
    void repairHint_doesNotEchoRawPlanSecret() {
        String secret = "SECRET_RAW_TOKEN_z9k";
        AtomicReference<SafePlanRepairHint> captured = new AtomicReference<>();
        PlanParseCoordinator c = new PlanParseCoordinator(planParser, hint -> {
            captured.set(hint);
            return PlanParseCoordinator.DEFAULT_EVAL_PLAN_JSON;
        });
        c.parseWithOptionalRepair(secret);
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().toCompactString()).doesNotContain(secret);
    }
}
