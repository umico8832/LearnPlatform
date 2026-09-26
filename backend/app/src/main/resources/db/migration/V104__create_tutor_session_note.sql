CREATE TABLE tutor_session_note (
    session_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    note VARCHAR(500) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (session_id),
    KEY idx_tutor_session_note_updated (updated_at, session_id),
    CONSTRAINT chk_tutor_session_note_revision CHECK (revision >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户显式保存的会话复盘，不复制学习事实';
