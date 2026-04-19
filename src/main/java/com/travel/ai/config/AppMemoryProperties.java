package com.travel.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 长期记忆与画像治理（{@code app.memory.*}）：默认关闭写入与注入，与 P0 隐私边界一致。
 */
@ConfigurationProperties(prefix = "app.memory")
public class AppMemoryProperties {

    private final LongTerm longTerm = new LongTerm();
    private final Profile profile = new Profile();
    private final AutoExtract autoExtract = new AutoExtract();

    public LongTerm getLongTerm() {
        return longTerm;
    }

    public Profile getProfile() {
        return profile;
    }

    public AutoExtract getAutoExtract() {
        return autoExtract;
    }

    public static class LongTerm {
        /**
         * {@code true} 时：允许从库中读取画像并（在 {@link #injectIntoPrompt} 为 true 时）注入主线 prompt。
         */
        private boolean enabled = false;
        /** 为 {@code false} 时即使 {@link #enabled} 为 true 也不注入 WRITE 前 prompt（仍可通过 HTTP 维护画像）。 */
        private boolean injectIntoPrompt = true;
        /** 不对这些主体名加载/注入画像（大小写敏感，与 {@link org.springframework.security.core.Authentication#getName()} 一致）。 */
        private List<String> skipUsernames = new ArrayList<>(List.of("eval-gateway", "anonymous"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isInjectIntoPrompt() {
            return injectIntoPrompt;
        }

        public void setInjectIntoPrompt(boolean injectIntoPrompt) {
            this.injectIntoPrompt = injectIntoPrompt;
        }

        public List<String> getSkipUsernames() {
            if (skipUsernames == null) {
                skipUsernames = new ArrayList<>(List.of("eval-gateway", "anonymous"));
            }
            return skipUsernames;
        }

        public void setSkipUsernames(List<String> skipUsernames) {
            this.skipUsernames = skipUsernames != null ? skipUsernames : new ArrayList<>();
        }
    }

    public static class Profile {
        private int maxSlots = 10;
        private int maxValueChars = 512;
        private int maxInjectChars = 1200;

        public int getMaxSlots() {
            return Math.min(50, Math.max(1, maxSlots));
        }

        public void setMaxSlots(int maxSlots) {
            this.maxSlots = maxSlots;
        }

        public int getMaxValueChars() {
            return Math.min(4000, Math.max(16, maxValueChars));
        }

        public void setMaxValueChars(int maxValueChars) {
            this.maxValueChars = maxValueChars;
        }

        public int getMaxInjectChars() {
            return Math.min(8000, Math.max(200, maxInjectChars));
        }

        public void setMaxInjectChars(int maxInjectChars) {
            this.maxInjectChars = maxInjectChars;
        }
    }

    /**
     * 从对话抽取画像：须显式开启；默认「仅待确认」不落库，避免静默写 PII。
     */
    public static class AutoExtract {
        /** 为 {@code false} 时关闭 LLM 抽取（含手动 {@code POST …/extract-suggestion} 与 after-chat 钩子）。 */
        private boolean enabled = false;
        /** 在每轮 SSE 正常结束后异步触发抽取（依赖 {@link #enabled}）。 */
        private boolean afterChat = false;
        /**
         * {@code true}：抽取结果写入 Redis 待确认，用户须 {@code POST …/confirm-extraction} 落库；
         * {@code false}：after-chat 路径下直接把 {@code mergedPreview} upsert 到 PG（高风险，仅建议内网调试）。
         */
        private boolean requireConfirm = true;
        private Duration pendingTtl = Duration.ofHours(1);
        private int llmTimeoutSeconds = 12;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAfterChat() {
            return afterChat;
        }

        public void setAfterChat(boolean afterChat) {
            this.afterChat = afterChat;
        }

        public boolean isRequireConfirm() {
            return requireConfirm;
        }

        public void setRequireConfirm(boolean requireConfirm) {
            this.requireConfirm = requireConfirm;
        }

        public Duration getPendingTtl() {
            if (pendingTtl == null || pendingTtl.isNegative() || pendingTtl.isZero()) {
                return Duration.ofHours(1);
            }
            return pendingTtl;
        }

        public void setPendingTtl(Duration pendingTtl) {
            this.pendingTtl = pendingTtl;
        }

        public int getLlmTimeoutSeconds() {
            return Math.min(120, Math.max(3, llmTimeoutSeconds));
        }

        public void setLlmTimeoutSeconds(int llmTimeoutSeconds) {
            this.llmTimeoutSeconds = llmTimeoutSeconds;
        }
    }
}
