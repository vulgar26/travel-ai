package com.travel.ai.feedback;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class FeedbackJdbcRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FeedbackJdbcRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public record InsertRow(
            String userId,
            String conversationIdOrNull,
            String thumbOrNull,
            Integer ratingOrNull,
            String commentOrNull,
            String evalCaseIdOrNull,
            List<String> evalTags,
            String requestIdOrNull
    ) {
    }

    public record LoadedRow(
            long id,
            String conversationId,
            String thumb,
            Integer rating,
            String comment,
            String evalCaseId,
            List<String> evalTags,
            String requestId,
            Instant createdAt
    ) {
    }

    public long insert(InsertRow row) {
        String tagsJson;
        try {
            tagsJson = objectMapper.writeValueAsString(row.evalTags() != null ? row.evalTags() : List.of());
        } catch (Exception e) {
            throw new IllegalStateException("eval_tags serialization failed", e);
        }
        Long id = jdbcTemplate.queryForObject(
                """
                        INSERT INTO user_feedback (
                            user_id, conversation_id, thumb, rating, comment_text,
                            eval_case_id, eval_tags, request_id, feedback_schema
                        ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, 1)
                        RETURNING id
                        """,
                Long.class,
                row.userId(),
                row.conversationIdOrNull(),
                row.thumbOrNull(),
                row.ratingOrNull(),
                row.commentOrNull(),
                row.evalCaseIdOrNull(),
                tagsJson,
                row.requestIdOrNull()
        );
        if (id == null) {
            throw new IllegalStateException("insert returned null id");
        }
        return id;
    }

    public Instant findCreatedAtById(long id) {
        Timestamp ts = jdbcTemplate.queryForObject(
                "SELECT created_at FROM user_feedback WHERE id = ?",
                Timestamp.class,
                id
        );
        return ts != null ? ts.toInstant() : Instant.now();
    }

    public List<LoadedRow> listByUserId(String userId, int limit, int offset) {
        return jdbcTemplate.query(
                """
                        SELECT id, conversation_id, thumb, rating, comment_text, eval_case_id,
                               eval_tags::text, request_id, created_at
                        FROM user_feedback
                        WHERE user_id = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> {
                    String tagsJson = rs.getString(7);
                    List<String> tags = parseTags(tagsJson);
                    Timestamp cat = rs.getTimestamp(9);
                    Instant created = cat != null ? cat.toInstant() : Instant.EPOCH;
                    return new LoadedRow(
                            rs.getLong(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getObject(4) != null ? rs.getInt(4) : null,
                            rs.getString(5),
                            rs.getString(6),
                            tags,
                            rs.getString(8),
                            created
                    );
                },
                userId,
                limit,
                offset
        );
    }

    private List<String> parseTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<>() {
            });
            return list != null ? List.copyOf(list) : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 测试或运维：按主键删一行（集成测清理）。 */
    public int deleteById(long id) {
        return jdbcTemplate.update("DELETE FROM user_feedback WHERE id = ?", id);
    }

    /** 测试：按用户删全部。 */
    public int deleteAllForUser(String userId) {
        return jdbcTemplate.update("DELETE FROM user_feedback WHERE user_id = ?", userId);
    }
}
