CREATE TABLE question_ask_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    attempt_id BIGINT NOT NULL,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    message VARCHAR(1000) NOT NULL,
    content MEDIUMTEXT NULL,
    suggestion JSON NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    error_code INT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_question_ask_request (attempt_id,request_id),
    KEY idx_question_ask_limit (user_id,question_id,status),
    CONSTRAINT chk_question_ask_status CHECK (status IN ('RUNNING','SUCCEEDED','FAILED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
