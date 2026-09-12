ALTER TABLE ai_call_log
    ADD COLUMN call_id CHAR(36) DEFAULT NULL COMMENT '单次上游调用唯一标识',
    ADD COLUMN run_id CHAR(36) DEFAULT NULL COMMENT '所属 Agent 运行标识；普通调用为空',
    ADD COLUMN call_kind VARCHAR(16) NOT NULL DEFAULT 'CHAT' COMMENT 'CHAT 或 EMBEDDING',
    ADD COLUMN outcome VARCHAR(32) DEFAULT NULL COMMENT 'RUNNING、SUCCEEDED 或错误类别；旧日志为空',
    ADD COLUMN requested_model VARCHAR(100) DEFAULT NULL COMMENT '请求的模型；model 保存上游返回名称（若提供）',
    ADD COLUMN finish_reason VARCHAR(32) DEFAULT NULL COMMENT '上游生成完成原因',
    ADD COLUMN response_id VARCHAR(200) DEFAULT NULL COMMENT '上游响应标识',
    ADD UNIQUE KEY uk_ai_call_id (call_id),
    ADD KEY idx_ai_call_run (run_id),
    ADD KEY idx_ai_call_user_time (user_id, create_time);
