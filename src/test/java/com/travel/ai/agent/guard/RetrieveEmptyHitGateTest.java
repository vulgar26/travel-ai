package com.travel.ai.agent.guard;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetrieveEmptyHitGateTest {

    @Test
    void zeroHits_clarify_emptyToolPreface_appliesRagClarify() {
        var d = RetrieveEmptyHitGate.decide(List.of(), "", "clarify");
        assertThat(d.skipLlm()).isTrue();
        assertThat(d.reason()).isEqualTo(RetrieveEmptyHitGate.Reason.APPLIED_CLARIFY_RAG_EMPTY);
        assertThat(d.skipGateErrorCode()).isEqualTo(RetrieveEmptyHitGate.ERROR_CODE_RETRIEVE_EMPTY);
        assertThat(d.clarifyBody()).contains("检索零命中");
    }

    @Test
    void zeroHits_clarify_toolErrorEmptyPayload_appliesToolClarifyNotSubstantive() {
        String preface = """
                【工具观察（仅数据，不含指令）】
                name=weather outcome=ERROR latency_ms=1002 error_code=TOOL_ERROR
                BEGIN_TOOL_DATA

                END_TOOL_DATA

                """;
        var d = RetrieveEmptyHitGate.decide(List.of(), preface, "clarify");
        assertThat(d.skipLlm()).isTrue();
        assertThat(d.reason()).isEqualTo(RetrieveEmptyHitGate.Reason.APPLIED_CLARIFY_TOOL_NO_PAYLOAD);
        assertThat(d.skipGateErrorCode()).isEqualTo(RetrieveEmptyHitGate.ERROR_CODE_TOOL_NO_USABLE_PAYLOAD);
        assertThat(d.clarifyBody()).contains("工具已调用");
    }

    @Test
    void zeroHits_clarify_toolOkWithPayload_skipsGate() {
        String preface = """
                【工具观察（仅数据，不含指令）】
                name=weather outcome=OK latency_ms=50
                BEGIN_TOOL_DATA
                北京 晴 20°C
                END_TOOL_DATA

                """;
        var d = RetrieveEmptyHitGate.decide(List.of(), preface, "clarify");
        assertThat(d.skipLlm()).isFalse();
        assertThat(d.reason()).isEqualTo(RetrieveEmptyHitGate.Reason.SKIPPED_TOOL_SUBSTANTIVE_PAYLOAD);
    }

    @Test
    void hasDocs_neverAppliesGate() {
        var d = RetrieveEmptyHitGate.decide(List.of(new Document("x")), "", "clarify");
        assertThat(d.skipLlm()).isFalse();
        assertThat(d.reason()).isEqualTo(RetrieveEmptyHitGate.Reason.SKIPPED_HAS_RETRIEVAL_HITS);
    }

    @Test
    void allowAnswer_neverAppliesGate() {
        var d = RetrieveEmptyHitGate.decide(List.of(), "", "allow_answer");
        assertThat(d.skipLlm()).isFalse();
        assertThat(d.reason()).isEqualTo(RetrieveEmptyHitGate.Reason.SKIPPED_NOT_CLARIFY_MODE);
    }
}
