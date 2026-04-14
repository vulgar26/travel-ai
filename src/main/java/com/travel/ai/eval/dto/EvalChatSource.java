package com.travel.ai.eval.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 评测响应 {@code sources[]}：由服务端从本次检索 hits 构造（禁止 LLM 生成/改写）。
 * <p>
 * 对齐 {@code plans/eval-upgrade.md}：{@code {id,title,snippet,score?}}。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvalChatSource {

    /** 稳定可回查标识（建议与 retrieval_hits[*].id 使用同一 canonical 口径）。 */
    private String id;

    /** 可选标题/来源名（展示用）。 */
    private String title;

    /** 原文片段的规则截断（例如 ≤300 字符），禁止模型摘要当证据。 */
    private String snippet;

    /** 可选：若不支持 score，保持为 null 且 {@code capabilities.retrieval.score=false}。 */
    private Double score;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}

