package com.travel.ai.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * 评测回放断点：表 {@code eval_conversation_checkpoint}（Flyway V3）。
 * <p>
 * 调用方负责 try/catch 与日志；本类不依赖评测 HTTP 路径。
 */
@Repository
public class EvalCheckpointRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EvalCheckpointRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * UPSERT 单行断点；{@code planRawSha256Hex} 须为 64 位小写 hex（与 {@link EvalChatService} 中 plan 快照一致）。
     */
    public void upsert(
            String conversationId,
            String planRawSha256Hex,
            String lastCompletedStage,
            int stageIndex,
            String configSnapshotHashOrNull,
            Map<String, ?> detail
    ) {
        Map<String, ?> safeDetail = detail != null ? detail : Map.of();
        String detailJson;
        try {
            detailJson = objectMapper.writeValueAsString(safeDetail);
        } catch (Exception e) {
            throw new IllegalStateException("eval checkpoint detail serialization failed", e);
        }
        short idx = (short) Math.max(0, Math.min(stageIndex, 32));
        jdbcTemplate.update(
                """
                        INSERT INTO eval_conversation_checkpoint (
                            conversation_id, plan_raw_sha256, last_completed_stage, stage_index,
                            config_snapshot_hash, detail, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?::jsonb, now(), now())
                        ON CONFLICT (conversation_id) DO UPDATE SET
                            plan_raw_sha256 = EXCLUDED.plan_raw_sha256,
                            last_completed_stage = EXCLUDED.last_completed_stage,
                            stage_index = EXCLUDED.stage_index,
                            config_snapshot_hash = EXCLUDED.config_snapshot_hash,
                            detail = EXCLUDED.detail,
                            updated_at = now()
                        """,
                conversationId,
                planRawSha256Hex,
                lastCompletedStage,
                idx,
                configSnapshotHashOrNull,
                detailJson
        );
    }

    /** 测试或运维清理用：按会话 id 删除一行。 */
    public int deleteByConversationId(String conversationId) {
        return jdbcTemplate.update("DELETE FROM eval_conversation_checkpoint WHERE conversation_id = ?", conversationId);
    }
}
