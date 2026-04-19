-- 长期记忆：按登录用户维度的可控画像（JSON 对象）；删除权见 DELETE /travel/profile
CREATE TABLE IF NOT EXISTS user_profile (
    user_id         varchar(128) PRIMARY KEY,
    schema_version  int          NOT NULL DEFAULT 1,
    payload         jsonb        NOT NULL DEFAULT '{}'::jsonb,
    updated_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_user_profile_updated_at ON user_profile (updated_at);
