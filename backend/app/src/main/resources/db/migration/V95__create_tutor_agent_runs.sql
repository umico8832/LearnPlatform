CREATE TABLE tutor_agent_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_key CHAR(36) NOT NULL,
    tutor_session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    next_sequence INT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tutor_agent_run_key (run_key),
    KEY idx_tutor_agent_run_session_user (tutor_session_id, user_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Tutor Agent 可恢复运行';

CREATE TABLE tutor_agent_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    sequence_no INT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tutor_agent_message_sequence (run_id, sequence_no),
    KEY idx_tutor_agent_message_run (run_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Tutor Agent 用户可见消息';
