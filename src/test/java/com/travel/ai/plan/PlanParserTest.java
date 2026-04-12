package com.travel.ai.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 附录 E / E2 风格样例与 Day4 容错解析契约。
 * <p>
 * SSOT：{@code plans/p0-execution-map.md} 附录 E（仓库外或后续落盘时以此为准）。
 */
class PlanParserTest {

    private PlanParser parser;

    /**
     * 与附录 E2「最小合法 Plan」等价的内联样例（字段名 snake_case，{@code plan_version} 为 v1）。
     */
    static final String APPENDIX_E2_MINIMAL = """
            {
              "plan_version": "v1",
              "goal": "为用户生成可执行的短途旅行计划草案",
              "steps": [
                {
                  "step_id": "s1",
                  "stage": "PLAN",
                  "instruction": "根据用户约束产出结构化行程草案",
                  "expected_output": "草案文本"
                },
                {
                  "step_id": "s2",
                  "stage": "RETRIEVE",
                  "instruction": "检索目的地与交通相关事实",
                  "tool": {
                    "name": "search_places",
                    "args": { "query": "京都 景点", "limit": 5 }
                  },
                  "expected_output": "候选地点列表"
                }
              ],
              "constraints": {
                "max_steps": 6,
                "total_timeout_ms": 120000,
                "tool_timeout_ms": 30000
              },
              "notes": "P0 线性阶段示例"
            }
            """;

    @BeforeEach
    void setUp() {
        parser = new PlanParser(new ObjectMapper());
    }

    @Test
    void parse_appendixE2Minimal_succeeds() throws Exception {
        PlanV1 plan = parser.parse(APPENDIX_E2_MINIMAL);
        assertThat(plan.planVersion()).isEqualTo("v1");
        assertThat(plan.goal()).contains("旅行计划");
        assertThat(plan.steps()).hasSize(2);
        assertThat(plan.steps().get(0).stepId()).isEqualTo("s1");
        assertThat(plan.steps().get(0).stage()).isEqualTo(PlanStage.PLAN);
        assertThat(plan.steps().get(0).hasTool()).isFalse();
        assertThat(plan.steps().get(1).hasTool()).isTrue();
        assertThat(plan.steps().get(1).tool().name()).isEqualTo("search_places");
        assertThat(plan.steps().get(1).tool().args()).containsEntry("query", "京都 景点");
        assertThat(plan.steps().get(1).tool().args()).containsEntry("limit", 5);
        assertThat(plan.constraints().maxSteps()).isEqualTo(6);
        assertThat(plan.constraints().totalTimeoutMs()).isEqualTo(120000);
        assertThat(plan.constraints().toolTimeoutMs()).isEqualTo(30000);
        assertThat(plan.notes()).contains("P0");
    }

    @Test
    void parse_extraTopLevelFields_ignored() throws Exception {
        String json = APPENDIX_E2_MINIMAL.replaceFirst("\\{", "{ \"llm_meta\": { \"x\": 1 }, ");
        PlanV1 plan = parser.parse(json);
        assertThat(plan.steps()).hasSize(2);
    }

    @Test
    void parse_markdownFence_stripped() throws Exception {
        String wrapped = "```json\n" + APPENDIX_E2_MINIMAL.trim() + "\n```";
        PlanV1 plan = parser.parse(wrapped);
        assertThat(plan.planVersion()).isEqualTo("v1");
    }

    @Test
    void parse_constraintsNumericStrings_coerced() throws Exception {
        String json = """
                {
                  "plan_version": "v1",
                  "steps": [
                    {
                      "step_id": "s1",
                      "stage": "WRITE",
                      "instruction": "输出最终答复"
                    }
                  ],
                  "constraints": {
                    "max_steps": "8",
                    "total_timeout_ms": "0",
                    "tool_timeout_ms": "100"
                  }
                }
                """;
        PlanV1 plan = parser.parse(json);
        assertThat(plan.constraints().maxSteps()).isEqualTo(8);
        assertThat(plan.constraints().totalTimeoutMs()).isZero();
        assertThat(plan.constraints().toolTimeoutMs()).isEqualTo(100);
    }

    @Test
    void parse_stageCaseInsensitive() throws Exception {
        String json = """
                {
                  "plan_version": "v1",
                  "steps": [
                    { "step_id": "s1", "stage": "guard", "instruction": "校验输出" }
                  ],
                  "constraints": { "max_steps": 1, "total_timeout_ms": 1, "tool_timeout_ms": 1 }
                }
                """;
        assertThat(parser.parse(json).steps().get(0).stage()).isEqualTo(PlanStage.GUARD);
    }

    @Test
    void parse_missingConstraints_throws() {
        String json = """
                {
                  "plan_version": "v1",
                  "steps": [
                    { "step_id": "s1", "stage": "PLAN", "instruction": "x" }
                  ]
                }
                """;
        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(PlanParseException.class)
                .hasMessageContaining("constraints");
    }

    @Test
    void parse_toolMissingArgs_throws() {
        String json = """
                {
                  "plan_version": "v1",
                  "steps": [
                    {
                      "step_id": "s1",
                      "stage": "TOOL",
                      "instruction": "call",
                      "tool": { "name": "t" }
                    }
                  ],
                  "constraints": { "max_steps": 1, "total_timeout_ms": 1, "tool_timeout_ms": 1 }
                }
                """;
        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(PlanParseException.class)
                .hasMessageContaining("args");
    }

    @Test
    void parse_emptyToolArgsObject_ok() throws Exception {
        String json = """
                {
                  "plan_version": "v1",
                  "steps": [
                    {
                      "step_id": "s1",
                      "stage": "TOOL",
                      "instruction": "call",
                      "tool": { "name": "noop", "args": {} }
                    }
                  ],
                  "constraints": { "max_steps": 1, "total_timeout_ms": 1, "tool_timeout_ms": 1 }
                }
                """;
        PlanV1 plan = parser.parse(json);
        assertThat(plan.steps().get(0).tool().args()).isEqualTo(Map.of());
    }
}
