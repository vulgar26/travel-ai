package com.travel.ai.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.travel.ai.config.AppMemoryProperties;
import com.travel.ai.config.RedisChatMemory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

@Service
public class UserProfileService {

    private static final int SCHEMA_VERSION = 1;

    private final UserProfileJdbcRepository repository;
    private final ObjectMapper objectMapper;
    private final AppMemoryProperties appMemoryProperties;
    private final ProfileExtractionService profileExtractionService;
    private final UserProfilePendingExtractionStore pendingExtractionStore;
    private final RedisChatMemory redisChatMemory;

    public UserProfileService(
            UserProfileJdbcRepository repository,
            ObjectMapper objectMapper,
            AppMemoryProperties appMemoryProperties,
            ProfileExtractionService profileExtractionService,
            UserProfilePendingExtractionStore pendingExtractionStore,
            RedisChatMemory redisChatMemory) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.appMemoryProperties = appMemoryProperties;
        this.profileExtractionService = profileExtractionService;
        this.pendingExtractionStore = pendingExtractionStore;
        this.redisChatMemory = redisChatMemory;
    }

    public UserProfileView getForCurrentUser() {
        String user = requireUsername();
        return loadView(user);
    }

    @Transactional
    public UserProfileView replaceForCurrentUser(JsonNode profileNode) {
        String user = requireUsername();
        if (profileNode == null || profileNode.isNull() || !profileNode.isObject()) {
            throw new IllegalArgumentException("body.profile must be a JSON object");
        }
        ObjectNode normalized = UserProfilePayloadValidator.validateCopy(
                (ObjectNode) profileNode,
                appMemoryProperties,
                objectMapper
        );
        repository.upsert(user, SCHEMA_VERSION, normalized);
        return new UserProfileView(SCHEMA_VERSION, normalized);
    }

    @Transactional
    public UserProfileView patchForCurrentUser(JsonNode profileNode) {
        String user = requireUsername();
        if (profileNode == null || profileNode.isNull() || !profileNode.isObject()) {
            throw new IllegalArgumentException("body.profile must be a JSON object");
        }
        ObjectNode patch = (ObjectNode) profileNode;
        UserProfilePayloadValidator.validatePatchKeys(patch);

        ObjectNode base = repository.findByUserId(user)
                .map(UserProfileJdbcRepository.UserProfileRow::payload)
                .map(ObjectNode::deepCopy)
                .orElseGet(objectMapper::createObjectNode);

        Iterator<Map.Entry<String, JsonNode>> it = patch.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode v = e.getValue();
            if (v == null || v.isNull()) {
                base.remove(e.getKey());
            } else {
                base.set(e.getKey(), v);
            }
        }

        ObjectNode normalized = UserProfilePayloadValidator.validateCopy(base, appMemoryProperties, objectMapper);
        repository.upsert(user, SCHEMA_VERSION, normalized);
        return new UserProfileView(SCHEMA_VERSION, normalized);
    }

    @Transactional
    public void deleteForCurrentUser(boolean clearChatMemory, String conversationId) {
        String user = requireUsername();
        repository.deleteByUserId(user);
        if (!clearChatMemory) {
            return;
        }
        if (conversationId != null && !conversationId.isBlank()) {
            redisChatMemory.clearForUser(user, conversationId.trim());
        } else {
            redisChatMemory.deleteAllConversationsForUser(user);
        }
    }

    /**
     * 异步 after-chat 或内部协调器调用：按用户名落库（无 {@code SecurityContext}）。
     */
    @Transactional
    public void upsertNormalizedForUser(String username, ObjectNode merged) {
        ObjectNode normalized = UserProfilePayloadValidator.validateCopy(merged, appMemoryProperties, objectMapper);
        repository.upsert(username, SCHEMA_VERSION, normalized);
    }

    /**
     * 手动触发：从 Redis 会话抽取；可选写入待确认。
     *
     * @throws IllegalArgumentException {@code app.memory.auto-extract.enabled=false} 时
     */
    public ExtractionSuggestionResult extractSuggestion(String conversationId, boolean saveAsPending) {
        if (!appMemoryProperties.getAutoExtract().isEnabled()) {
            throw new IllegalArgumentException("profile extraction is disabled (app.memory.auto-extract.enabled=false)");
        }
        String user = requireUsername();
        String requestId = java.util.UUID.randomUUID().toString();
        ProfileExtractionService.ExtractionResult result = profileExtractionService.extract(user, conversationId, requestId);
        ObjectNode current = repository.findByUserId(user)
                .map(UserProfileJdbcRepository.UserProfileRow::payload)
                .map(ObjectNode::deepCopy)
                .orElseGet(objectMapper::createObjectNode);
        boolean pendingSaved = false;
        if (saveAsPending
                && !result.isEmptySuggestion()
                && appMemoryProperties.getAutoExtract().isRequireConfirm()) {
            var payload = new UserProfilePendingExtractionStore.PendingPayload(
                    result.suggestedPatch().deepCopy(),
                    result.mergedPreview().deepCopy(),
                    System.currentTimeMillis()
            );
            pendingExtractionStore.save(user, conversationId, payload, appMemoryProperties.getAutoExtract().getPendingTtl());
            pendingSaved = true;
        }
        return new ExtractionSuggestionResult(
                result.suggestedPatch().deepCopy(),
                result.mergedPreview().deepCopy(),
                current,
                pendingSaved
        );
    }

    public Optional<PendingExtractionView> getPendingExtraction(String conversationId) {
        String user = requireUsername();
        return pendingExtractionStore.load(user, conversationId)
                .map(p -> new PendingExtractionView(
                        p.createdAtEpochMs(),
                        p.suggestedPatch().deepCopy(),
                        p.mergedPreview().deepCopy()
                ));
    }

    @Transactional
    public UserProfileView confirmPendingExtraction(String conversationId) {
        String user = requireUsername();
        UserProfilePendingExtractionStore.PendingPayload p = pendingExtractionStore.load(user, conversationId)
                .orElseThrow(() -> new IllegalArgumentException("no pending extraction for this conversation"));
        ObjectNode merged = UserProfilePayloadValidator.validateCopy(p.mergedPreview(), appMemoryProperties, objectMapper);
        repository.upsert(user, SCHEMA_VERSION, merged);
        pendingExtractionStore.delete(user, conversationId);
        return new UserProfileView(SCHEMA_VERSION, merged);
    }

    public void discardPendingExtraction(String conversationId) {
        pendingExtractionStore.delete(requireUsername(), conversationId);
    }

    public String buildPromptPrefixBlock(String username) {
        var lt = appMemoryProperties.getLongTerm();
        if (!lt.isEnabled() || !lt.isInjectIntoPrompt()) {
            return "";
        }
        if (username == null || username.isBlank()) {
            return "";
        }
        for (String skip : lt.getSkipUsernames()) {
            if (skip != null && skip.equals(username)) {
                return "";
            }
        }
        OptionalPayload block = loadPayloadForPrompt(username);
        if (block.payloadText.isEmpty()) {
            return "";
        }
        int max = appMemoryProperties.getProfile().getMaxInjectChars();
        String body = block.payloadText;
        if (body.length() > max) {
            body = body.substring(0, max) + "…[truncated]";
        }
        return "【用户长期偏好（与本轮问题无关时可忽略）】\n" + body + "\n\n";
    }

    private OptionalPayload loadPayloadForPrompt(String username) {
        return repository.findByUserId(username)
                .filter(row -> row.payload() != null && row.payload().size() > 0)
                .map(row -> {
                    try {
                        return new OptionalPayload(objectMapper.writeValueAsString(row.payload()));
                    } catch (Exception e) {
                        return new OptionalPayload("");
                    }
                })
                .orElse(new OptionalPayload(""));
    }

    private UserProfileView loadView(String userId) {
        return repository.findByUserId(userId)
                .map(row -> new UserProfileView(row.schemaVersion(), row.payload().deepCopy()))
                .orElseGet(() -> new UserProfileView(SCHEMA_VERSION, objectMapper.createObjectNode()));
    }

    private static String requireUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("unauthenticated");
        }
        return auth.getName();
    }

    private record OptionalPayload(String payloadText) {
    }

    public record UserProfileView(int schemaVersion, ObjectNode profile) {
    }

    public record ExtractionSuggestionResult(
            ObjectNode suggestedPatch,
            ObjectNode mergedPreview,
            ObjectNode currentProfile,
            boolean pendingSaved
    ) {
    }

    public record PendingExtractionView(long createdAtEpochMs, ObjectNode suggestedPatch, ObjectNode mergedPreview) {
    }
}
