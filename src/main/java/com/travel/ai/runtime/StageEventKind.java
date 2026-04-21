package com.travel.ai.runtime;

/**
 * 阶段事件类型：用于 SSE 流式可观测与 eval 汇总对齐。
 */
public enum StageEventKind {
    START,
    END,
    SKIP,
    INFO,
    ERROR
}

