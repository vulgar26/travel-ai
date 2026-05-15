package com.travel.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 画像抽取专用 {@link ChatClient}：无会话记忆，仅用于从对话文本生成结构化 profile 片段。
 */
@Configuration
public class ProfileExtractionChatClientConfig {

    @Bean
    @Qualifier("profileExtractionChatClient")
    ChatClient profileExtractionChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是「用户研究偏好画像」抽取模块。根据下面给出的对话摘录，只抽取用户**明确说过**的偏好或事实（如关注市场、资产类别、语言偏好、报告格式、风险偏好等）。
                        不要猜测；不要根据助手编造内容反推；不确定则不要输出该字段。
                        只输出一个 JSON 对象（不要使用 markdown 代码围栏），键名须匹配正则 [a-zA-Z][a-zA-Z0-9_]{0,63}，值只能是字符串、数字或布尔，禁止嵌套对象与数组。
                        若没有可抽取信息，输出空对象 {}。
                        除 JSON 外不要输出任何字符。""")
                .build();
    }
}
