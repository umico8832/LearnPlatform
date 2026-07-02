-- AI 运营提醒持久化：由管理端运营报告生成，支持确认处理和历史追踪。
CREATE TABLE ai_usage_alert (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '提醒ID',
    level VARCHAR(20) NOT NULL COMMENT '提醒级别：INFO/WARNING',
    alert_type VARCHAR(50) NOT NULL COMMENT '提醒类型，如 HIGH_FAILURE_RATE',
    message VARCHAR(500) NOT NULL COMMENT '提醒内容',
    period_days INT NOT NULL COMMENT '统计周期天数',
    period_start DATETIME NOT NULL COMMENT '当前统计周期开始时间',
    period_end DATETIME NOT NULL COMMENT '当前统计周期结束时间',
    metric_snapshot TEXT DEFAULT NULL COMMENT '触发提醒时的关键指标快照JSON',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN/ACKNOWLEDGED',
    acknowledged_by BIGINT DEFAULT NULL COMMENT '确认提醒的管理员ID',
    acknowledged_time DATETIME DEFAULT NULL COMMENT '确认时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (id),
    KEY idx_status_period (status, period_end),
    KEY idx_type_period (alert_type, period_start, period_end),
    KEY idx_acknowledged_by (acknowledged_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI运营提醒表';
