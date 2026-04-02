package com.powernode.springmvc.controller;

import com.powernode.springmvc.agent.TravelAgent;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/travel")
public class TravelController {

    @Autowired
    private TravelAgent travelAgent;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @GetMapping(value = "/chat/{conversationId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<ServerSentEvent<String>> chat(
            HttpServletResponse response,
            @PathVariable String conversationId,
            @RequestParam String query) {
        // System.out.println("收到请求：" + query);
        response.setCharacterEncoding("UTF-8");
        // 降低代理缓冲、避免中间层缓存流式响应；Connection 由容器维持长连接
        response.setHeader("Cache-Control", "no-cache, no-store");
        response.setHeader("X-Accel-Buffering", "no");
        return travelAgent.chat(conversationId, query);
    }
}
