package com.travel.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 评测专用配置（{@code app.eval.*}），与 {@link AppAgentProperties}（{@code app.agent.*}）分离，避免键前缀混用。
 * <p>
 * 绑定键见 {@code application.yml} 中 {@code app.eval} 段。
 */
@ConfigurationProperties(prefix = "app.eval")
public class AppEvalProperties {

    private String gatewayKey = "";
    /** 评测 TOOL stub 的 {@code Future#get} 上限（毫秒），与 {@code app.agent.tool-timeout} 取 min，见 {@link com.travel.ai.eval.EvalToolStageRunner}。 */
    private long toolTimeoutMs = 100L;
    /**
     * 仅测试用：评测 stub 主路径入口 sleep（毫秒）；生产须为 {@code 0}。
     */
    private long stubWorkSleepMs = 0L;

    /**
     * 是否在 eval 响应中写入 {@code meta.recovery_action}（及场景下的 {@code meta.self_check}）；关则完全不写字段，便于旧 harness。
     */
    private boolean reflectionMetaEnabled = true;

    /**
     * 是否允许评测口以“真实 LLM 调用模式”运行（用于获取 provider usage 真值）。默认 false：避免 CI/本地误触发外网调用与成本。
     */
    private boolean llmRealEnabled = false;

    public String getGatewayKey() {
        return gatewayKey != null ? gatewayKey : "";
    }

    public void setGatewayKey(String gatewayKey) {
        this.gatewayKey = gatewayKey;
    }

    public long getToolTimeoutMs() {
        return toolTimeoutMs;
    }

    public void setToolTimeoutMs(long toolTimeoutMs) {
        this.toolTimeoutMs = toolTimeoutMs;
    }

    public long getStubWorkSleepMs() {
        return stubWorkSleepMs;
    }

    public void setStubWorkSleepMs(long stubWorkSleepMs) {
        this.stubWorkSleepMs = stubWorkSleepMs;
    }

    public boolean isReflectionMetaEnabled() {
        return reflectionMetaEnabled;
    }

    public void setReflectionMetaEnabled(boolean reflectionMetaEnabled) {
        this.reflectionMetaEnabled = reflectionMetaEnabled;
    }

    public boolean isLlmRealEnabled() {
        return llmRealEnabled;
    }

    public void setLlmRealEnabled(boolean llmRealEnabled) {
        this.llmRealEnabled = llmRealEnabled;
    }
}
