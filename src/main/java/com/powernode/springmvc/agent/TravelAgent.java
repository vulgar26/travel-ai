package com.powernode.springmvc.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.powernode.springmvc.config.RedisChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class TravelAgent {

    private final ChatClient chatClient;

    @Autowired
    private RedisChatMemory chatMemory;


    private static final  String SYSTEM_PROMPT = """
            你是一个专业的出行规划助手。
            当用户提供出发地、目的地、时间、预算等信息时，你需要：
            1. 分析用户的出行需求
            2. 提供多种出行方案（飞机、高铁、自驾等）
            3. 对每种方案给出大致费用、时长、优缺点
            4. 根据用户预算和偏好推荐最适合的方案
            请用清晰的结构化格式回答，方便用户阅读。
            """;

    public TravelAgent(ChatClient.Builder builder, RedisChatMemory chatMemory) {
        System.out.println("注入的ChatMemory类型：" + chatMemory.getClass().getName());
        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultOptions(
                        DashScopeChatOptions.builder()
                                .topP(0.7)
                                .build()
                )
                .build();
    }

    public Flux<String> chat(String conversationId, String userMessage) {
        System.out.println("调用AI，conversationId：" + conversationId + "，message：" + userMessage);
        return chatClient.prompt(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }
}
