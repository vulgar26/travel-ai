package com.travel.ai.agent.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 主线 PLAN 阶段：调用无记忆 {@link ChatClient} 产出结构化 Plan JSON（轻量 Plan-and-Execute 的第一步）。
 */
@Component
public class MainLinePlanProposer {

    private static final Logger log = LoggerFactory.getLogger(MainLinePlanProposer.class);

    private final ChatClient planChatClient;
    private final ObjectMapper objectMapper;

    public MainLinePlanProposer(
            @Qualifier("mainLinePlanChatClient") ChatClient planChatClient,
            ObjectMapper objectMapper
    ) {
        this.planChatClient = planChatClient;
        this.objectMapper = objectMapper;
    }

    /**
     * @return 模型原始 JSON 文本（已去掉常见 ``` 围栏）；失败时抛出由调用方降级
     */
    public String proposePlanJson(String userMessage, String requestId) {
        String raw = planChatClient.prompt()
                .user("用户输入：\n" + (userMessage == null ? "" : userMessage))
                .call()
                .content();
        String stripped = stripMarkdownFence(raw == null ? "" : raw.trim());
        validateJsonShape(stripped, requestId);
        return stripped;
    }

    private static String stripMarkdownFence(String raw) {
        String t = raw.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            t = firstNl > 0 ? t.substring(firstNl + 1).trim() : t.substring(3).trim();
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3).trim();
            }
        }
        return t;
    }

    private void validateJsonShape(String json, String requestId) throws IllegalArgumentException {
        try {
            JsonNode n = objectMapper.readTree(json);
            if (!n.isObject() || !n.has("steps") || !n.get("steps").isArray()) {
                throw new IllegalArgumentException("plan json missing object.steps array");
            }
        } catch (Exception e) {
            log.warn("[plan] parse_shape_failed requestId={} preview={}", requestId, preview(json));
            throw new IllegalArgumentException("invalid plan json", e);
        }
    }

    private static String preview(String s) {
        if (s.length() <= 160) {
            return s;
        }
        return s.substring(0, 160) + "…";
    }
}
