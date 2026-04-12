package com.travel.ai.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 将 LLM 输出的 JSON 解析为 {@link PlanV1}。
 * <p>
 * <b>SSOT</b>：{@code plans/p0-execution-map.md} <b>附录 E</b>（JSON Schema）。本解析器仅实现附录 E 的骨架校验与容错读取；
 * 与附录冲突时以附录 E 为准。
 * <p>
 * 容错行为（Day4）：去除首尾空白；剥离 markdown 代码围栏（{@code ```json ... ```}）；忽略未知顶层/步骤内字段；
 * 对 {@code constraints} 中的整型字段，允许 JSON 数字或十进制整数字符串。
 */
@Component
public class PlanParser {

    private static final int MIN_STEPS = 1;
    private static final int MAX_STEPS = 8;
    private static final int MIN_MAX_STEPS = 1;
    private static final int MAX_MAX_STEPS = 20;

    private final ObjectMapper mapper;

    public PlanParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 解析附录 E 兼容的 JSON 字符串。
     *
     * @param raw 可能含 markdown 围栏的 JSON
     * @return 非 null 的 {@link PlanV1}
     * @throws PlanParseException 结构、必填字段或枚举不合法时
     */
    public PlanV1 parse(String raw) throws PlanParseException {
        if (raw == null) {
            throw new PlanParseException("plan json is null");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new PlanParseException("plan json is blank");
        }
        String json = stripMarkdownFences(trimmed);
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new PlanParseException("invalid json: " + e.getOriginalMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new PlanParseException("root must be a JSON object");
        }
        ObjectNode obj = (ObjectNode) root;

        String planVersion = requireText(obj, "plan_version");
        if (!"v1".equals(planVersion)) {
            throw new PlanParseException("plan_version must be \"v1\", got: " + planVersion);
        }

        String goal = optionalText(obj.get("goal"));
        String notes = optionalText(obj.get("notes"));

        JsonNode stepsNode = obj.get("steps");
        if (stepsNode == null || !stepsNode.isArray()) {
            throw new PlanParseException("steps must be a non-null array");
        }
        int n = stepsNode.size();
        if (n < MIN_STEPS || n > MAX_STEPS) {
            throw new PlanParseException("steps length must be in [" + MIN_STEPS + "," + MAX_STEPS + "], got " + n);
        }
        List<PlanStepV1> steps = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            steps.add(parseStep(stepsNode.get(i), i));
        }

        JsonNode c = obj.get("constraints");
        if (c == null || !c.isObject()) {
            throw new PlanParseException("constraints must be a non-null object");
        }
        PlanConstraintsV1 constraints = parseConstraints((ObjectNode) c);

        return new PlanV1(planVersion, goal, List.copyOf(steps), constraints, notes);
    }

    private PlanStepV1 parseStep(JsonNode node, int index) throws PlanParseException {
        if (node == null || !node.isObject()) {
            throw new PlanParseException("steps[" + index + "] must be an object");
        }
        ObjectNode o = (ObjectNode) node;
        String stepId = requireText(o, "step_id");
        String stageRaw = requireText(o, "stage");
        PlanStage stage;
        try {
            stage = PlanStage.valueOf(stageRaw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new PlanParseException("steps[" + index + "].stage invalid: " + stageRaw);
        }
        String instruction = requireText(o, "instruction");
        if (instruction.isBlank()) {
            throw new PlanParseException("steps[" + index + "].instruction must be non-blank");
        }

        PlanToolV1 tool = null;
        JsonNode toolNode = o.get("tool");
        if (toolNode != null && !toolNode.isNull()) {
            if (!toolNode.isObject()) {
                throw new PlanParseException("steps[" + index + "].tool must be object or absent");
            }
            tool = parseTool((ObjectNode) toolNode, index);
        }

        String expectedOutput = optionalText(o.get("expected_output"));
        return new PlanStepV1(stepId, stage, instruction, tool, expectedOutput);
    }

    private PlanToolV1 parseTool(ObjectNode o, int stepIndex) throws PlanParseException {
        String name = requireText(o, "name");
        JsonNode argsNode = o.get("args");
        if (argsNode == null || argsNode.isNull()) {
            throw new PlanParseException("steps[" + stepIndex + "].tool.args is required when tool is present");
        }
        if (!argsNode.isObject()) {
            throw new PlanParseException("steps[" + stepIndex + "].tool.args must be an object");
        }
        Map<String, Object> args = objectNodeToStringObjectMap((ObjectNode) argsNode);
        return new PlanToolV1(name, args);
    }

    private static Map<String, Object> objectNodeToStringObjectMap(ObjectNode o) {
        Map<String, Object> m = new HashMap<>();
        Iterator<String> it = o.fieldNames();
        while (it.hasNext()) {
            String k = it.next();
            JsonNode v = o.get(k);
            m.put(k, jsonNodeToScalarOrStructure(v));
        }
        return Collections.unmodifiableMap(m);
    }

    private static Object jsonNodeToScalarOrStructure(JsonNode v) {
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isBoolean()) {
            return v.booleanValue();
        }
        if (v.isInt()) {
            return v.intValue();
        }
        if (v.isLong()) {
            return v.longValue();
        }
        if (v.isDouble() || v.isFloat()) {
            return v.doubleValue();
        }
        if (v.isTextual()) {
            return v.asText();
        }
        if (v.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode e : v) {
                list.add(jsonNodeToScalarOrStructure(e));
            }
            return Collections.unmodifiableList(list);
        }
        if (v.isObject()) {
            return objectNodeToStringObjectMap((ObjectNode) v);
        }
        return v.toString();
    }

    private PlanConstraintsV1 parseConstraints(ObjectNode o) throws PlanParseException {
        int maxSteps = requirePositiveInt(o, "max_steps", MIN_MAX_STEPS, MAX_MAX_STEPS);
        int totalTimeoutMs = requireNonNegativeInt(o, "total_timeout_ms");
        int toolTimeoutMs = requireNonNegativeInt(o, "tool_timeout_ms");
        return new PlanConstraintsV1(maxSteps, totalTimeoutMs, toolTimeoutMs);
    }

    private static int requirePositiveInt(ObjectNode o, String field, int minInclusive, int maxInclusive)
            throws PlanParseException {
        int v = requireNonNegativeInt(o, field);
        if (v < minInclusive || v > maxInclusive) {
            throw new PlanParseException(field + " must be in [" + minInclusive + "," + maxInclusive + "], got " + v);
        }
        return v;
    }

    private static int requireNonNegativeInt(ObjectNode o, String field) throws PlanParseException {
        JsonNode n = o.get(field);
        if (n == null || n.isNull()) {
            throw new PlanParseException("missing required field: " + field);
        }
        if (n.isInt() || n.isLong()) {
            int v = n.intValue();
            if (v < 0) {
                throw new PlanParseException(field + " must be >= 0, got " + v);
            }
            return v;
        }
        if (n.isTextual()) {
            String s = n.asText().trim();
            try {
                int v = Integer.parseInt(s);
                if (v < 0) {
                    throw new PlanParseException(field + " must be >= 0, got " + v);
                }
                return v;
            } catch (NumberFormatException e) {
                throw new PlanParseException(field + " must be integer or decimal int string, got: " + s);
            }
        }
        throw new PlanParseException(field + " must be integer or decimal int string");
    }

    private static String requireText(ObjectNode o, String field) throws PlanParseException {
        JsonNode n = o.get(field);
        if (n == null || n.isNull()) {
            throw new PlanParseException("missing required field: " + field);
        }
        if (!n.isTextual()) {
            throw new PlanParseException("field " + field + " must be a string");
        }
        return n.asText();
    }

    private static String optionalText(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (!n.isTextual()) {
            return null;
        }
        String t = n.asText();
        return t.isEmpty() ? null : t;
    }

    /**
     * 去除可选的 markdown 围栏；若首行以 {@code ```} 开头则去掉首行与匹配的末行。
     */
    static String stripMarkdownFences(String s) {
        String t = s.trim();
        if (!t.startsWith("```")) {
            return t;
        }
        int firstNl = t.indexOf('\n');
        if (firstNl < 0) {
            return t;
        }
        String rest = t.substring(firstNl + 1);
        int lastFence = rest.lastIndexOf("```");
        if (lastFence < 0) {
            return t;
        }
        return rest.substring(0, lastFence).trim();
    }
}
