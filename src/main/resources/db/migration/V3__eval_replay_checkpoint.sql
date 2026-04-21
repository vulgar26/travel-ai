-- 评测 / 回放断点（P1 harness：仅持久化骨架；写入与读取见 docs/eval/EVAL_REPLAY_CHECKPOINT.md）
-- 与 Redis 会话记忆正交：此处面向「可审计、可对账」的线性阶段进度与 plan 指纹。
CREATE TABLE IF NOT EXISTS eval_conversation_checkpoint (
    conversation_id       varchar(128) PRIMARY KEY,
    plan_raw_sha256       char(64)     NOT NULL,
    last_completed_stage  varchar(32)  NOT NULL,
    stage_index           smallint     NOT NULL DEFAULT 0
        CHECK (stage_index >= 0 AND stage_index <= 32),
    config_snapshot_hash  varchar(64),
    detail                jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_eval_ckpt_plan_hash
    ON eval_conversation_checkpoint (plan_raw_sha256);

CREATE INDEX IF NOT EXISTS idx_eval_ckpt_updated_at
    ON eval_conversation_checkpoint (updated_at DESC);
