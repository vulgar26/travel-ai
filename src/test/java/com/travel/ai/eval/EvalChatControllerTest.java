package com.travel.ai.eval;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Day1：校验 {@code POST /api/v1/eval/chat} 非流式 JSON 与 snake_case 字段名。
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
                .andExpect(jsonPath("$.answer").isString())
                .andExpect(jsonPath("$.behavior").value("answer"))
                .andExpect(jsonPath("$.latency_ms").isNumber())
                .andExpect(jsonPath("$.capabilities.retrieval.supported").value(true))
                .andExpect(jsonPath("$.capabilities.streaming.ttft").value(false))
                .andExpect(jsonPath("$.capabilities.guardrails.quote_only").value(false))
                .andExpect(jsonPath("$.meta.mode").value("AGENT"))
                .andExpect(jsonPath("$.meta.request_id").isString());
    }

    @Test
    void blankQueryReturnsClarify() throws Exception {
        mockMvc.perform(post("/api/v1/eval/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behavior").value("clarify"))
                .andExpect(jsonPath("$.latency_ms").isNumber());
    }
}
