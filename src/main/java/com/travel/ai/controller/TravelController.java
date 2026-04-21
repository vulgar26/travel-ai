package com.travel.ai.controller;

import com.travel.ai.agent.TravelAgent;
import com.travel.ai.config.AppConversationProperties;
import com.travel.ai.controller.dto.TravelChatRequest;
import com.travel.ai.conversation.ConversationIdValidator;
import com.travel.ai.conversation.ConversationRegistry;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 出行 SSE 入口：仅委托 {@link TravelAgent}，不在此层保留未使用的 ChatClient.Builder（升级 P5-1）。
 * <p>
 * 推荐 {@link #chatPost}（{@code POST} + JSON body）；{@link #chat}（{@code GET} + {@code query}）保留兼容并带 {@code Deprecation} 响应头。
 */
@RestController
@RequestMapping("/travel")
public class TravelController {

    @Autowired
    private TravelAgent travelAgent;

    @Autowired
    private ConversationRegistry conversationRegistry;

    @Autowired
    private AppConversationProperties appConversationProperties;

    /**
     * 服务端签发并登记 {@code conversationId}；生产在 {@code app.conversation.require-registration=true} 时，
     * 客户端须先调用本接口再订阅 {@code GET|POST /travel/chat/{conversationId}}。
     */
    @PostMapping("/conversations")
    public ResponseEntity<Map<String, String>> createConversation() {
        String id = conversationRegistry.createAndRegister();
        return ResponseEntity.ok(Map.of("conversationId", id));
    }

    /**
     * 推荐：SSE 对话，query 放在 JSON body，无 URL 长度上限问题、减少访问日志中的明文 query。
     */
    @PostMapping(value = "/chat/{conversationId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<ServerSentEvent<String>> chatPost(
            HttpServletResponse response,
            @PathVariable String conversationId,
            @RequestBody TravelChatRequest request) {
        assertConversationAllowed(conversationId);
        String query = request != null ? request.getQuery() : null;
        String normalized = validateAndNormalizeQuery(query);
        stampSseHeaders(response);
        return travelAgent.chat(conversationId, normalized);
    }

    /**
     * 兼容路径：通过查询参数传 query；已标记弃用，请迁移到 {@link #chatPost}。
     */
    @Deprecated
    @GetMapping(value = "/chat/{conversationId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<ServerSentEvent<String>> chat(
            HttpServletResponse response,
            @PathVariable String conversationId,
            @RequestParam String query) {
        assertConversationAllowed(conversationId);
        String normalized = validateAndNormalizeQuery(query);
        response.setHeader("Deprecation", "true");
        response.setHeader("Link", "</travel/chat/" + conversationId + ">; rel=\"alternate\"; type=\"application/json\"");
        stampSseHeaders(response);
        return travelAgent.chat(conversationId, normalized);
    }

    private void assertConversationAllowed(String conversationId) {
        if (!ConversationIdValidator.isValid(conversationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid conversationId");
        }
        if (appConversationProperties.isRequireRegistration()
                && !conversationRegistry.isRegistered(conversationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "conversation not registered for this user");
        }
    }

    private String validateAndNormalizeQuery(String query) {
        if (query == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query is required");
        }
        String q = query.trim();
        if (q.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be blank");
        }
        int max = appConversationProperties.getMaxQueryChars();
        if (max > 0 && q.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "query exceeds max length (" + max + " characters)");
        }
        return q;
    }

    private static void stampSseHeaders(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store");
        response.setHeader("X-Accel-Buffering", "no");
    }
}
