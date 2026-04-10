package com.travel.ai.eval;

import com.travel.ai.eval.dto.EvalChatMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Day1–Day3：契约、snake_case，以及 {@code meta} 可观测稳定性（{@code stage_order} / {@code step_count} / {@code replan_count}）。
 * <p>
 * {@code @WebMvcTest} 会拉起 Spring Security 与 {@link com.travel.ai.security.JwtAuthFilter}，但不会加载真实
 * {@code @Service JwtService}。用 {@link EvalChatControllerTestConfig} 显式注册一个 {@code JwtService} Bean（Mock），
 * 比 {@code @MockBean} 更早、更稳定地参与依赖注入；{@code addFilters=false} 表示 MockMvc 不跑过滤器链，只测 Controller+JSON。
 */
@WebMvcTest(controllers = EvalChatController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({EvalChatService.class, EvalChatControllerTestConfig.class})
class EvalChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void chatReturnsSnakeCaseAndRequiredFields() throws Exception {
        String body = """
                {"query":"上海三日游","mode":"AGENT","conversation_id":"c1"}
                """;
        mockMvc.perform(post("/api/v1/eval/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("charset=UTF-8")))
                .andExpect(jsonPath("$.answer").isString())
                .andExpect(jsonPath("$.behavior").value("answer"))
                .andExpect(jsonPath("$.latency_ms").isNumber())
                .andExpect(jsonPath("$.capabilities.retrieval.supported").value(true))
                .andExpect(jsonPath("$.capabilities.streaming.ttft").value(false))
                .andExpect(jsonPath("$.capabilities.guardrails.quote_only").value(false))
                .andExpect(jsonPath("$.meta.mode").value("AGENT"))
                .andExpect(jsonPath("$.meta.request_id").isString())
                .andExpect(jsonPath("$.meta.stage_order.length()").value(5))
                .andExpect(jsonPath("$.meta.stage_order[0]").value("PLAN"))
                .andExpect(jsonPath("$.meta.stage_order[4]").value("GUARD"))
                .andExpect(jsonPath("$.meta.step_count").value(5))
                .andExpect(jsonPath("$.meta.replan_count").value(EvalChatMeta.P0_REPLAN_COUNT));
    }

    /**
     * Day3：多组输入下 {@code replan_count} 恒为 P0 固定值，且 {@code step_count == stage_order.length}。
     */
    static Stream<Arguments> metaObservabilityCases() {
        return Stream.of(
                Arguments.of("{\"query\":\"上海\",\"mode\":\"AGENT\"}", 5),
                Arguments.of("{\"query\":\"仅一行\",\"mode\":\"EVAL\"}", 5),
                Arguments.of("{\"query\":\"  \"}", 0),
                Arguments.of("{\"query\":\"\"}", 0)
        );
    }

    @ParameterizedTest
    @MethodSource("metaObservabilityCases")
    void replanCountAlwaysP0_andStepCountEqualsStageOrderLength(String jsonBody, int expectedStageCount) throws Exception {
        mockMvc.perform(post("/api/v1/eval/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("charset=UTF-8")))
                .andExpect(jsonPath("$.meta.replan_count").value(EvalChatMeta.P0_REPLAN_COUNT))
                .andExpect(jsonPath("$.meta.step_count").value(expectedStageCount))
                .andExpect(jsonPath("$.meta.stage_order.length()").value(expectedStageCount));
    }

    @Test
    void blankQueryReturnsClarify() throws Exception {
        mockMvc.perform(post("/api/v1/eval/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("charset=UTF-8")))
                .andExpect(jsonPath("$.behavior").value("clarify"))
                .andExpect(jsonPath("$.latency_ms").isNumber())
                .andExpect(jsonPath("$.meta.stage_order.length()").value(0))
                .andExpect(jsonPath("$.meta.step_count").value(0))
                .andExpect(jsonPath("$.meta.replan_count").value(EvalChatMeta.P0_REPLAN_COUNT));
    }
}
