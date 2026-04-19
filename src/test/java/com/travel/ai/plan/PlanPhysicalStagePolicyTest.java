package com.travel.ai.plan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanPhysicalStagePolicyTest {

    private static PlanStepV1 s(PlanStage stage) {
        return new PlanStepV1("id", stage, "instruction", null, null);
    }

    private static PlanConstraintsV1 c() {
        return new PlanConstraintsV1(8, 60_000, 10_000);
    }

    @Test
    void writeOnly_skipsRetrieveToolImplicitGuard() {
        PlanV1 p = new PlanV1("v1", "g", List.of(s(PlanStage.WRITE)), c(), null);
        PlanPhysicalStagePolicy.PhysicalStageFlags f = PlanPhysicalStagePolicy.resolve(p);
        assertThat(f.runRetrieve()).isFalse();
        assertThat(f.runTool()).isFalse();
        assertThat(f.runGuard()).isFalse();
    }

    @Test
    void retrieveOnly_impliesGuardWithoutExplicitGuardStage() {
        PlanV1 p = new PlanV1("v1", "g", List.of(s(PlanStage.RETRIEVE), s(PlanStage.WRITE)), c(), null);
        PlanPhysicalStagePolicy.PhysicalStageFlags f = PlanPhysicalStagePolicy.resolve(p);
        assertThat(f.runRetrieve()).isTrue();
        assertThat(f.runTool()).isFalse();
        assertThat(f.runGuard()).isTrue();
    }

    @Test
    void explicitGuard_withoutRetrieve_showsGuard() {
        PlanV1 p = new PlanV1("v1", "g", List.of(s(PlanStage.GUARD), s(PlanStage.WRITE)), c(), null);
        PlanPhysicalStagePolicy.PhysicalStageFlags f = PlanPhysicalStagePolicy.resolve(p);
        assertThat(f.runRetrieve()).isFalse();
        assertThat(f.runGuard()).isTrue();
    }

    @Test
    void allStagesPresent() {
        PlanV1 p = new PlanV1("v1", "g", List.of(
                s(PlanStage.RETRIEVE),
                s(PlanStage.TOOL),
                s(PlanStage.GUARD),
                s(PlanStage.WRITE)
        ), c(), null);
        PlanPhysicalStagePolicy.PhysicalStageFlags f = PlanPhysicalStagePolicy.resolve(p);
        assertThat(f.runRetrieve()).isTrue();
        assertThat(f.runTool()).isTrue();
        assertThat(f.runGuard()).isTrue();
    }
}
