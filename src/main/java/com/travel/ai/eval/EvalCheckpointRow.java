package com.travel.ai.eval;

import java.util.Map;

/**
 * {@link EvalCheckpointRepository} 读取的一行断点（不含 conversation_id，调用方已知）。
 */
public record EvalCheckpointRow(
        String planRawSha256,
        String lastCompletedStage,
        int stageIndex,
        String configSnapshotHash,
        Map<String, Object> detail
) {
    public boolean resumeEligible() {
        Object v = detail != null ? detail.get("resume_eligible") : null;
        return Boolean.TRUE.equals(v);
    }
}
