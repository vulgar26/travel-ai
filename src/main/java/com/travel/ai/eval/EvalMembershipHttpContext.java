package com.travel.ai.eval;

/**
 * eval-upgrade.md 评测请求头：用于 per-case {@code k_case} 派生与 hashed membership。
 */
public record EvalMembershipHttpContext(String token, String targetId, String datasetId, String caseId) {

    public static EvalMembershipHttpContext empty() {
        return new EvalMembershipHttpContext(null, null, null, null);
    }

    public static EvalMembershipHttpContext fromHeaders(
            String xEvalToken,
            String xEvalTargetId,
            String xEvalDatasetId,
            String xEvalCaseId
    ) {
        return new EvalMembershipHttpContext(
                blankToNull(xEvalToken),
                blankToNull(xEvalTargetId),
                blankToNull(xEvalDatasetId),
                blankToNull(xEvalCaseId)
        );
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 四项齐全时才写入 {@code meta.retrieval_hit_id_hashes}（空数组与缺省等价，避免误伤契约）。
     */
    public boolean completeForHashedMembership() {
        return token != null && targetId != null && datasetId != null && caseId != null;
    }
}
