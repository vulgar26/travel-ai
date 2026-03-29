package com.powernode.springmvc.controller;

import com.powernode.springmvc.agent.TravelAgent;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/travel")
public class TravelController {

    @Autowired
    private TravelAgent travelAgent;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @GetMapping(value = "/chat/{conversationId}", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chat(
            HttpServletResponse response,
            @PathVariable String conversationId,
            @RequestParam String query) {
        // System.out.println("收到请求：" + query);
        response.setCharacterEncoding("UTF-8");
        return travelAgent.chat(conversationId, query);
    }
}
