package com.travel.ai.eval;

import com.travel.ai.agent.QueryRewriter;
import com.travel.ai.config.AppAgentProperties;
import com.travel.ai.config.AppEvalProperties;
import com.travel.ai.plan.PlanParseCoordinator;
import com.travel.ai.plan.PlanParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code app.eval.config-snapshot-meta-enabled=true} 时，评测 {@code meta.config_snapshot} 与 hash 同源白名单。
 */
@WebMvcTest(controllers = EvalChatController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "app.eval.tool-timeout-ms=50",
        "app.eval.llm-real-enabled=false",
        "app.eval.config-snapshot-meta-enabled=true"
})
@EnableConfigurationProperties({AppAgentProperties.class, AppEvalProperties.class})
@Import({
        EvalChatService.class,
        PlanParseCoordinator.class,
        PlanParser.class,
        EvalToolStageRunner.class,
        EvalChatControllerTestConfig.class,
        EvalChatTimeoutExecutorConfig.class
})
class EvalChatConfigSnapshotMetaMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VectorStore vectorStore;

    @MockBean
    private QueryRewriter queryRewriter;

    @MockBean(name = "evalUsageChatClient")
    private org.springframework.ai.chat.client.ChatClient evalUsageChatClient;

    @BeforeEach
    void stubs() {
        lenient().when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        lenient().when(queryRewriter.rewrite(any())).thenReturn(List.of("stub-rewrite"));
        lenient().when(queryRewriter.rewriteWithOutcome(any())).thenReturn(
                new QueryRewriter.RewriteOutcome(List.of("stub-rewrite"), false));
    }

    @Test
    void metaConfigSnapshotObject_presentWhenEnabled() throws Exception {
        String body = """
                {"query":"评测：config snapshot meta should exist","mode":"EVAL"}
                """;
        mockMvc.perform(post("/api/v1/eval/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.config_snapshot_hash").isString())
                .andExpect(jsonPath("$.meta.config_snapshot_id", matchesPattern(
                        "travel-ai:config-snapshot/v1/sha256/[a-f0-9]{64}")))
                .andExpect(jsonPath("$.meta.config_snapshot['app.agent.max-steps']").exists())
                .andExpect(jsonPath("$.meta.config_snapshot['app.eval.llm-real-enabled']").value("false"));
    }
}
