-- NULL 继承 ai.daily-quota 全局默认值；0 表示该用户 AI 调用不限次数。
ALTER TABLE user
    ADD COLUMN ai_daily_quota INT DEFAULT NULL COMMENT '用户级AI每日调用配额，NULL继承全局配置，0不限次数' AFTER status;
