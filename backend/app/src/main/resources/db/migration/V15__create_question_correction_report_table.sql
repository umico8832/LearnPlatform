-- 正式题目纠错反馈记录：用户发现题干、答案、解析或知识点问题后提交，管理员处理并留痕。
CREATE TABLE question_correction_report (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '纠错记录ID',
    question_id BIGINT NOT NULL COMMENT '题目ID',
    reporter_id BIGINT NOT NULL COMMENT '提交纠错的用户ID',
    report_type VARCHAR(30) NOT NULL COMMENT '纠错类型：CONTENT/ANSWER/ANALYSIS/KNOWLEDGE_POINT/OTHER',
    description VARCHAR(1000) NOT NULL COMMENT '问题描述',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN/RESOLVED/REJECTED',
    handler_id BIGINT DEFAULT NULL COMMENT '处理管理员ID',
    handler_comment VARCHAR(1000) DEFAULT NULL COMMENT '处理说明',
    handled_time DATETIME DEFAULT NULL COMMENT '处理时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (id),
    KEY idx_question_status (question_id, status),
    KEY idx_reporter_time (reporter_id, create_time),
    KEY idx_status_time (status, create_time),
    KEY idx_handler_id (handler_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题目纠错反馈表';
