package com.travel.ai.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserProfileJdbcRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public UserProfileJdbcRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<UserProfileRow> findByUserId(String userId) {
        List<UserProfileRow> rows = jdbcTemplate.query(
                "SELECT schema_version, payload::text FROM user_profile WHERE user_id = ?",
                (rs, rowNum) -> {
                    try {
                        int sv = rs.getInt(1);
                        String json = rs.getString(2);
                        ObjectNode payload = (ObjectNode) objectMapper.readTree(json != null ? json : "{}");
                        return new UserProfileRow(sv, payload);
                    } catch (Exception e) {
                        throw new IllegalStateException("user_profile payload corrupt for user_id=" + userId, e);
                    }
                },
                userId
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    public void upsert(String userId, int schemaVersion, ObjectNode payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        jdbcTemplate.update(
                """
                        INSERT INTO user_profile (user_id, schema_version, payload, updated_at)
                        VALUES (?, ?, ?::jsonb, now())
                        ON CONFLICT (user_id) DO UPDATE SET
                          schema_version = EXCLUDED.schema_version,
                          payload = EXCLUDED.payload,
                          updated_at = now()
                        """,
                userId,
                schemaVersion,
                json
        );
    }

    public int deleteByUserId(String userId) {
        return jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
    }

    public record UserProfileRow(int schemaVersion, ObjectNode payload) {
    }
}
