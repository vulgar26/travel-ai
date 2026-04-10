package com.travel.ai.controller;

import com.travel.ai.agent.TravelAgent;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 出行 SSE 入口：仅委托 {@link TravelAgent}，不在此层保留未使用的 ChatClient.Builder（升级 P5-1）。
 */
@RestController
@RequestMapping("/travel")
public class TravelController {

    @Autowired
    private TravelAgent travelAgent;

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
