package com.travel.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 评测口（可选 real LLM）专用 {@link ChatClient}：不带会话记忆，只用于获取 provider usage（token 真值）。
 * <p>
 * 默认评测口仍为 stub；仅当 {@code app.eval.llm-real-enabled=true} 且请求 {@code llm_mode=real} 才会触发一次调用。
 */
@Configuration
public class EvalUsageChatClientConfig {

    @Bean
    @Qualifier("evalUsageChatClient")
    ChatClient evalUsageChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是评测口的 usage 探针：请用一句话回答用户问题。\n
                        注意：该回答不会作为正式评测输出，仅用于触发 provider usage 统计。""")
                .build();
    }
}

