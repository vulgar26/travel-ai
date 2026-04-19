package com.travel.ai.controller;

import com.travel.ai.agent.TravelAgent;
import com.travel.ai.config.AppConversationProperties;
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
     * 客户端须先调用本接口再订阅 {@code GET /travel/chat/{conversationId}}。
     */
    @PostMapping("/conversations")
    public ResponseEntity<Map<String, String>> createConversation() {
        String id = conversationRegistry.createAndRegister();
        return ResponseEntity.ok(Map.of("conversationId", id));
    }

    @GetMapping(value = "/chat/{conversationId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<ServerSentEvent<String>> chat(
            HttpServletResponse response,
            @PathVariable String conversationId,
            @RequestParam String query) {
        if (!ConversationIdValidator.isValid(conversationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid conversationId");
        }
        if (appConversationProperties.isRequireRegistration()
                && !conversationRegistry.isRegistered(conversationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "conversation not registered for this user");
        }
        // System.out.println("收到请求：" + query);
        response.setCharacterEncoding("UTF-8");
        // 降低代理缓冲、避免中间层缓存流式响应；Connection 由容器维持长连接
        response.setHeader("Cache-Control", "no-cache, no-store");
        response.setHeader("X-Accel-Buffering", "no");
        return travelAgent.chat(conversationId, query);
    }
}
