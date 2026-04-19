package com.travel.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 主线 PLAN 阶段专用 {@link ChatClient}：不带 {@link org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor}，
 * 避免把「规划草稿」写入用户会话记忆。
 */
@Configuration
public class MainLinePlanChatClientConfig {

    @Bean
    @Qualifier("mainLinePlanChatClient")
    ChatClient mainLinePlanChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是出行对话管线的「规划-only」模块：只做意图与阶段排序，不执行检索/工具。
                        只输出一段紧凑 JSON（不要使用 markdown 代码围栏），结构严格如下：
                        {"intent":"一句中文意图","steps":["PLAN","RETRIEVE","TOOL","GUARD","WRITE"]的子序列（大写英文枚举）,"rationale":"为何选此序列"}
                        steps 必须至少包含 RETRIEVE 与 WRITE；若用户明确需要实时天气/气温等，应包含 TOOL；否则可省略 TOOL。
                        除 JSON 外不要输出任何字符。""")
                .build();
    }
}
