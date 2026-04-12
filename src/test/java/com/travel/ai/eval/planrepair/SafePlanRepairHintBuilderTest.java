package com.travel.ai.eval.planrepair;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.travel.ai.plan.PlanParseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafePlanRepairHintBuilderTest {

    @Test
    void jsonSyntaxCause_usesGenericMessageWithoutOriginalSnippet() {
        JsonProcessingException jpe = new JsonProcessingException("Unexpected character ('x') at line 1") {};
        PlanParseException ex = new PlanParseException("invalid json: wrapped", jpe);
        SafePlanRepairHint h = SafePlanRepairHintBuilder.from(ex);
        assertThat(h.errorCode()).isEqualTo("PARSE_ERROR");
        assertThat(h.violations()).hasSize(1);
        assertThat(h.violations().get(0).message()).contains("not valid JSON");
        assertThat(h.violations().get(0).message()).doesNotContain("Unexpected character");
    }
}
