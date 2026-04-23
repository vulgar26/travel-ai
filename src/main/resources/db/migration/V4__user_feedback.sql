-- P1-3：用户对回答的反馈（点赞/点踩/评分/短评），与可选 eval 归因字段联动；按 JWT user_id 隔离。
CREATE TABLE IF NOT EXISTS user_feedback (
    id                 bigserial PRIMARY KEY,
    user_id            varchar(128) NOT NULL,
    conversation_id    varchar(128),
    thumb              varchar(8),
    rating             smallint,
    comment_text       text,
    eval_case_id       varchar(256),
    eval_tags          jsonb        NOT NULL DEFAULT '[]'::jsonb,
    request_id         varchar(128),
    feedback_schema    smallint     NOT NULL DEFAULT 1,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT user_feedback_thumb_ck CHECK (thumb IS NULL OR thumb IN ('up', 'down')),
    CONSTRAINT user_feedback_rating_ck CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5)),
    CONSTRAINT user_feedback_payload_ck CHECK (
        thumb IS NOT NULL
        OR rating IS NOT NULL
        OR (comment_text IS NOT NULL AND btrim(comment_text) <> '')
    )
);

CREATE INDEX IF NOT EXISTS idx_user_feedback_user_created
    ON user_feedback (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_feedback_conversation
    ON user_feedback (conversation_id)
    WHERE conversation_id IS NOT NULL;
