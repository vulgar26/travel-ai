package com.travel.ai.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EvalLlmRealTagPolicyTest {

    @Test
    void requireDisabled_allowsWithoutTags() {
        assertNull(EvalLlmRealTagPolicy.tagGateFailureReasonOrNull(false, List.of("cost/"), null));
        assertNull(EvalLlmRealTagPolicy.tagGateFailureReasonOrNull(false, List.of("cost/"), List.of()));
    }

    @Test
    void requireEnabled_noTags_blocked() {
        assertEquals(
                "tag_gate_no_tags",
                EvalLlmRealTagPolicy.tagGateFailureReasonOrNull(true, List.of("cost/"), null));
        assertEquals(
                "tag_gate_no_tags",
                EvalLlmRealTagPolicy.tagGateFailureReasonOrNull(true, List.of("cost/"), List.of()));
    }

    @Test
    void requireEnabled_noPrefixMatch_blocked() {
        assertEquals(
                "tag_gate_no_match",
                EvalLlmRealTagPolicy.tagGateFailureReasonOrNull(true, List.of("cost/"), List.of("p0/smoke", "rag/foo")));
    }

    @Test
    void requireEnabled_prefixMatch_allowed() {
        assertNull(EvalLlmRealTagPolicy.tagGateFailureReasonOrNull(true, List.of("cost/"), List.of("p0/x", "cost/smoke")));
        assertNull(EvalLlmRealTagPolicy.tagGateFailureReasonOrNull(true, List.of("cost/", "billing/"), List.of("billing/001")));
    }
}
