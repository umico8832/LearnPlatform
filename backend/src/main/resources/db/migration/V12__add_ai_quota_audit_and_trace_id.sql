-- AI 配额变更须可审计；AI 调用日志关联 HTTP 请求追踪号，便于运营问题定位。
CREATE TABLE ai_quota_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审计记录ID',
    user_id BIGINT NOT NULL COMMENT '被调整配额的用户ID',
    admin_user_id BIGINT NOT NULL COMMENT '执行调整的管理员ID',
    previous_daily_quota INT DEFAULT NULL COMMENT '调整前配额，NULL表示继承全局值',
    new_daily_quota INT DEFAULT NULL COMMENT '调整后配额，NULL表示继承全局值',
    reason VARCHAR(500) NOT NULL COMMENT '管理员调整原因',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_create_time (user_id, create_time),
    KEY idx_admin_create_time (admin_user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户AI日配额调整审计日志';

ALTER TABLE ai_call_log
    ADD COLUMN trace_id VARCHAR(32) DEFAULT NULL COMMENT '关联HTTP请求追踪ID' AFTER duration,
    ADD KEY idx_trace_id (trace_id);
