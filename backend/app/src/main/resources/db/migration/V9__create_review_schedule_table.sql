-- 间隔重复复习计划表（SM-2 算法）
CREATE TABLE IF NOT EXISTS question_review_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    question_id BIGINT NOT NULL COMMENT '题目ID',
    ease_factor DECIMAL(4,2) NOT NULL DEFAULT 2.50 COMMENT 'SM-2 简易因子（最低1.30）',
    interval_days INT NOT NULL DEFAULT 0 COMMENT '当前间隔天数',
    repetitions INT NOT NULL DEFAULT 0 COMMENT '连续正确次数',
    next_review_date DATE NOT NULL COMMENT '下次复习日期',
    last_review_date DATE DEFAULT NULL COMMENT '上次复习日期',
    last_quality INT DEFAULT NULL COMMENT '上次答题质量(0-5)',
    total_reviews INT NOT NULL DEFAULT 0 COMMENT '总复习次数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    UNIQUE KEY uk_user_question (user_id, question_id),
    INDEX idx_user_next_review (user_id, next_review_date, deleted),
    INDEX idx_question_id (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='间隔重复复习计划表';