package com.travel.ai.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.travel.ai.config.AppMemoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * 从 {@link ChatMemory} 中的会话文本调用无记忆 {@link ChatClient} 抽取画像字段；结果须经校验，默认仅写入待确认 Redis。
 */
@Service
public class ProfileExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ProfileExtractionService.class);

    private static final int MAX_TRANSCRIPT_CHARS = 12_000;
    private static final int MAX_LINE_CHARS = 2_000;

    private final ChatClient profileExtractionChatClient;
    private final ChatMemory chatMemory;
    private final ObjectMapper objectMapper;
    private final AppMemoryProperties appMemoryProperties;
    private final UserProfileJdbcRepository userProfileJdbcRepository;

    public ProfileExtractionService(
            @Qualifier("profileExtractionChatClient") ChatClient profileExtractionChatClient,
            ChatMemory chatMemory,
            ObjectMapper objectMapper,
            AppMemoryProperties appMemoryProperties,
            UserProfileJdbcRepository userProfileJdbcRepository) {
        this.profileExtractionChatClient = profileExtractionChatClient;
        this.chatMemory = chatMemory;
        this.objectMapper = objectMapper;
        this.appMemoryProperties = appMemoryProperties;
        this.userProfileJdbcRepository = userProfileJdbcRepository;
    }

    /**
     * 基于当前 Redis 会话内容抽取；不写入数据库（调用方负责 pending 或直接 upsert）。
     */
    public ExtractionResult extract(String username, String conversationId, String requestId) {
        if (!appMemoryProperties.getAutoExtract().isEnabled()) {
            return ExtractionResult.empty(objectMapper);
        }
        if (shouldSkipUsername(username)) {
            return ExtractionResult.empty(objectMapper);
        }
        String transcript = buildTranscript(conversationId);
        if (transcript.isBlank()) {
            log.debug("[profile-extract] empty_transcript conversationId={} requestId={}", conversationId, requestId);
            return ExtractionResult.empty(objectMapper);
        }
        int timeoutSec = appMemoryProperties.getAutoExtract().getLlmTimeoutSeconds();
        String raw = Mono.fromCallable(() -> profileExtractionChatClient.prompt()
                        .user("以下为对话摘录（含用户与助手轮次），请按系统指令只输出 JSON 对象：\n" + transcript)
                        .call()
                        .content())
                .timeout(Duration.ofSeconds(Math.max(3, timeoutSec)))
                .onErrorResume(t -> {
                    log.warn("[profile-extract] llm_timeout_or_error conversationId={} requestId={} err={}",
                            conversationId, requestId, t.toString());
                    return Mono.just("{}");
                })
                .block();

        ObjectNode suggested = parseSuggestedObject(raw, requestId);
        if (suggested.size() == 0) {
            return emptyWithCurrent(username);
        }
        ObjectNode suggestedNormalized;
        try {
            suggestedNormalized = UserProfilePayloadValidator.validateCopy(
                    suggested, appMemoryProperties, objectMapper);
        } catch (IllegalArgumentException ex) {
            log.info("[profile-extract] suggested_rejected conversationId={} requestId={} msg={}",
                    conversationId, requestId, ex.getMessage());
            return emptyWithCurrent(username);
        }
        if (suggestedNormalized.size() == 0) {
            return emptyWithCurrent(username);
        }

        ObjectNode current = userProfileJdbcRepository.findByUserId(username)
                .map(UserProfileJdbcRepository.UserProfileRow::payload)
                .map(ObjectNode::deepCopy)
                .orElseGet(objectMapper::createObjectNode);
        ObjectNode merged = mergeShallow(current, suggestedNormalized);
        try {
            merged = UserProfilePayloadValidator.validateCopy(merged, appMemoryProperties, objectMapper);
        } catch (IllegalArgumentException ex) {
            log.info("[profile-extract] merged_rejected conversationId={} requestId={} msg={}",
                    conversationId, requestId, ex.getMessage());
            return emptyWithCurrent(username);
        }
        return new ExtractionResult(suggestedNormalized, merged);
    }

    private ExtractionResult emptyWithCurrent(String username) {
        ObjectNode current = userProfileJdbcRepository.findByUserId(username)
                .map(UserProfileJdbcRepository.UserProfileRow::payload)
                .map(ObjectNode::deepCopy)
                .orElseGet(objectMapper::createObjectNode);
        return new ExtractionResult(objectMapper.createObjectNode(), current);
    }

    private boolean shouldSkipUsername(String username) {
        if (username == null || username.isBlank()) {
            return true;
        }
        for (String s : appMemoryProperties.getLongTerm().getSkipUsernames()) {
            if (s != null && s.equals(username)) {
                return true;
            }
        }
        return false;
    }

    private String buildTranscript(String conversationId) {
        List<Message> messages = chatMemory.get(conversationId);
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            String role;
            String text;
            if (m instanceof UserMessage um) {
                role = "用户";
                text = um.getText();
            } else if (m instanceof AssistantMessage am) {
                role = "助手";
                text = am.getText();
            } else {
                continue;
            }
            if (text == null) {
                text = "";
            }
            if (text.length() > MAX_LINE_CHARS) {
                text = text.substring(0, MAX_LINE_CHARS) + "…[truncated]";
            }
            sb.append("[").append(role).append("]\n").append(text).append("\n\n");
            if (sb.length() >= MAX_TRANSCRIPT_CHARS) {
                break;
            }
        }
        return sb.toString().trim();
    }

    private ObjectNode parseSuggestedObject(String raw, String requestId) {
        if (raw == null) {
            return objectMapper.createObjectNode();
        }
        String stripped = stripMarkdownFence(raw.trim());
        try {
            JsonNode n = objectMapper.readTree(stripped);
            if (!n.isObject()) {
                log.info("[profile-extract] parse_not_object requestId={} preview={}", requestId, preview(stripped));
                return objectMapper.createObjectNode();
            }
            return (ObjectNode) n;
        } catch (Exception e) {
            log.info("[profile-extract] parse_failed requestId={} preview={} err={}",
                    requestId, preview(stripped), e.getMessage());
            return objectMapper.createObjectNode();
        }
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

    private static String preview(String s) {
        if (s.length() <= 120) {
            return s;
        }
        return s.substring(0, 120) + "…";
    }

    private static ObjectNode mergeShallow(ObjectNode base, ObjectNode patch) {
        ObjectNode out = base.deepCopy();
        var it = patch.fields();
        while (it.hasNext()) {
            var e = it.next();
            out.set(e.getKey(), e.getValue());
        }
        return out;
    }

    public record ExtractionResult(ObjectNode suggestedPatch, ObjectNode mergedPreview) {
        public static ExtractionResult empty(ObjectMapper om) {
            ObjectNode empty = om.createObjectNode();
            return new ExtractionResult(empty, empty.deepCopy());
        }

        public boolean isEmptySuggestion() {
            return suggestedPatch == null || suggestedPatch.size() == 0;
        }
    }
}
