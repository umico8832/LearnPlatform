-- AI 调用日志只保存可审计元数据，不保存原始 prompt 或响应正文。
ALTER TABLE ai_call_log
    ADD COLUMN prompt_template VARCHAR(100) DEFAULT NULL COMMENT 'Prompt 模板或功能标识，不含原始提示词内容' AFTER trace_id,
    ADD COLUMN prompt_hash CHAR(64) DEFAULT NULL COMMENT 'system/user prompt 的 SHA-256 指纹，不可反推出原文' AFTER prompt_template,
    ADD COLUMN model_config_version CHAR(64) DEFAULT NULL COMMENT '调用时模型相关配置指纹' AFTER prompt_hash,
    ADD KEY idx_prompt_template (prompt_template),
    ADD KEY idx_model_config_version (model_config_version);
