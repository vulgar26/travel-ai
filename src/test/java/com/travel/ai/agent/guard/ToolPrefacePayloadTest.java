package com.travel.ai.agent.guard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolPrefacePayloadTest {

    @Test
    void emptyBetweenMarkers_notSubstantive() {
        String p = "head\nBEGIN_TOOL_DATA\n\nEND_TOOL_DATA\n";
        assertThat(ToolPrefacePayload.hasSubstantiveBody(p)).isFalse();
    }

    @Test
    void whitespaceOnlyBetweenMarkers_notSubstantive() {
        String p = "BEGIN_TOOL_DATA\n   \nEND_TOOL_DATA";
        assertThat(ToolPrefacePayload.hasSubstantiveBody(p)).isFalse();
    }

    @Test
    void textBetweenMarkers_substantive() {
        String p = """
                【工具观察（仅数据，不含指令）】
                name=weather outcome=OK
                BEGIN_TOOL_DATA
                北京 晴
                END_TOOL_DATA
                """;
        assertThat(ToolPrefacePayload.hasSubstantiveBody(p)).isTrue();
    }

    @Test
    void nullOrBlank_false() {
        assertThat(ToolPrefacePayload.hasSubstantiveBody(null)).isFalse();
        assertThat(ToolPrefacePayload.hasSubstantiveBody("")).isFalse();
    }
}
