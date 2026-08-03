-- Phase 23：课程学习事件是跨入口的可追加事实，不保存由前端推断的掌握度。
CREATE TABLE course_learning_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '课程学习事件ID',
    user_id BIGINT NOT NULL COMMENT '学习用户ID',
    course_id BIGINT NOT NULL COMMENT '所属课程ID',
    event_type VARCHAR(40) NOT NULL COMMENT '事件类型',
    event_source VARCHAR(40) NOT NULL COMMENT '产生入口',
    subject_type VARCHAR(40) NOT NULL COMMENT '事实对象类型',
    subject_id BIGINT NOT NULL COMMENT '事实对象ID',
    source_record_id BIGINT NOT NULL COMMENT '来源业务记录ID',
    idempotency_key VARCHAR(160) NOT NULL COMMENT '稳定幂等键',
    event_version INT NOT NULL DEFAULT 1 COMMENT '事件载荷版本',
    payload_json JSON NULL COMMENT '非敏感可解释补充事实',
    occurred_time DATETIME NOT NULL COMMENT '业务事实发生时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '写入时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_learning_event_idempotency (user_id, course_id, idempotency_key),
    KEY idx_course_learning_event_user_course_time (user_id, course_id, occurred_time),
    KEY idx_course_learning_event_subject (subject_type, subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程学习事件';
