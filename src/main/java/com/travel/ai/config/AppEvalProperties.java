package com.travel.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    /**
     * 评测口 real LLM 调用的额外超时上限（毫秒）。\n
     * 默认较小，避免“为了拿 usage”拖慢整段评测；设为 0 表示禁用额外上限（不推荐）。
     */
    private long llmRealTimeoutMs = 1200L;

    /**
     * 是否在 {@code llm_mode=real} 且服务端允许时，要求请求体 {@code eval_tags} 至少命中一条
     * {@link #llmRealRequiredTagPrefixes} 前缀后才触发 usage 探针。\n
     * 默认 true：避免跑批时每行都触发外网调用；评测平台可对抽样行打 {@code cost/...} 类 tag。
     */
    private boolean llmRealRequireTagMatch = true;

    /**
     * 与 {@link #llmRealRequireTagMatch} 联用：允许触发 real usage 探针的 eval 标签前缀（任一 tag 以任一前缀开头即通过）。\n
     * 未配置或过滤后为空时，默认仅 {@code cost/}（与成本/用量抽样 harness 对齐）。
     */
    private List<String> llmRealRequiredTagPrefixes;

    /**
     * 是否在评测响应 {@code meta} 中额外写入 {@code config_snapshot}（与 {@code config_snapshot_hash} 计算用字符串同源的键值映射）。\n
     * 默认 {@code false}：避免 meta 膨胀、以及与仅比对 hash 的旧 harness 产生噪声差异。
     */
    private boolean configSnapshotMetaEnabled = false;

    /**
     * 是否在评测成功组装响应后写入 {@code eval_conversation_checkpoint}（须请求体带非空 {@code conversation_id} 且 plan 已解析）。\n
     * 默认 {@code true}；写库失败仅打日志，不影响 eval HTTP。切片测试无 DataSource 时无 Repository Bean，自然跳过。
     */
    private boolean checkpointPersistenceEnabled = true;

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

    public long getLlmRealTimeoutMs() {
        return llmRealTimeoutMs;
    }

    public void setLlmRealTimeoutMs(long llmRealTimeoutMs) {
        this.llmRealTimeoutMs = llmRealTimeoutMs;
    }

    public boolean isLlmRealRequireTagMatch() {
        return llmRealRequireTagMatch;
    }

    public void setLlmRealRequireTagMatch(boolean llmRealRequireTagMatch) {
        this.llmRealRequireTagMatch = llmRealRequireTagMatch;
    }

    public List<String> getLlmRealRequiredTagPrefixes() {
        return llmRealRequiredTagPrefixes;
    }

    public void setLlmRealRequiredTagPrefixes(List<String> llmRealRequiredTagPrefixes) {
        this.llmRealRequiredTagPrefixes = llmRealRequiredTagPrefixes;
    }

    /**
     * {@link #llmRealRequireTagMatch} 为 false 时返回空列表（表示不做前缀门禁）；否则返回非空前缀列表（含默认 {@code cost/}）。
     */
    public List<String> effectiveLlmRealRequiredTagPrefixes() {
        if (!llmRealRequireTagMatch) {
            return List.of();
        }
        if (llmRealRequiredTagPrefixes == null || llmRealRequiredTagPrefixes.isEmpty()) {
            return List.of("cost/");
        }
        List<String> cleaned = llmRealRequiredTagPrefixes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return cleaned.isEmpty() ? List.of("cost/") : Collections.unmodifiableList(cleaned);
    }

    public boolean isConfigSnapshotMetaEnabled() {
        return configSnapshotMetaEnabled;
    }

    public void setConfigSnapshotMetaEnabled(boolean configSnapshotMetaEnabled) {
        this.configSnapshotMetaEnabled = configSnapshotMetaEnabled;
    }

    public boolean isCheckpointPersistenceEnabled() {
        return checkpointPersistenceEnabled;
    }

    public void setCheckpointPersistenceEnabled(boolean checkpointPersistenceEnabled) {
        this.checkpointPersistenceEnabled = checkpointPersistenceEnabled;
    }
}
