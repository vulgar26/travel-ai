package com.travel.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会话 ID 策略：服务端登记 + 可选强校验（{@code app.conversation.require-registration}）。
 */
@ConfigurationProperties(prefix = "app.conversation")
public class AppConversationProperties {

    /**
     * {@code true}：仅允许已通过 {@code POST /travel/conversations} 登记到当前用户的 {@code conversationId} 调用
     * {@code GET|POST /travel/chat/{conversationId}}；{@code false}：保持旧行为（演示/兼容）。
     */
    private boolean requireRegistration = false;

    /**
     * 聊天 {@code query} 最大长度（UTF-16 码元数，与 {@link String#length()} 一致）。\n
     * 适用于 {@code GET ...?query=} 与 {@code POST} JSON body；超出返回 400。
     */
    private int maxQueryChars = 8192;

    public boolean isRequireRegistration() {
        return requireRegistration;
    }

    public void setRequireRegistration(boolean requireRegistration) {
        this.requireRegistration = requireRegistration;
    }

    public int getMaxQueryChars() {
        return maxQueryChars;
    }

    public void setMaxQueryChars(int maxQueryChars) {
        this.maxQueryChars = maxQueryChars;
    }
}
