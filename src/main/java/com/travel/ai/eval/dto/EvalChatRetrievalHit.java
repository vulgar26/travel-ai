package com.travel.ai.eval.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 评测响应根级 {@code retrieval_hits[]}：本轮用于 citation membership 的候选集合（至少包含 {@code id}）。
 * <p>
 * 对齐 P0+ 执行规范（见 {@code plans/p0-plus-execution.md} §16）：eval 侧可用其前 N 条做 membership 校验。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvalChatRetrievalHit {

    /** 稳定 canonical id（与 {@code sources[*].id} 必须同口径）。 */
    private String id;

    /** 可选：标题/来源名。 */
    private String title;

    /** 可选：检索 score（若不支持，保持 null）。 */
    private Double score;

    public EvalChatRetrievalHit() {
    }

    public EvalChatRetrievalHit(String id, String title, Double score) {
        this.id = id;
        this.title = title;
        this.score = score;
    }

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

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}

