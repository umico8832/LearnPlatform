-- 保留上游真实 usage 的输入/输出拆分，并固化调用当时按模型价格计算的成本。
ALTER TABLE ai_call_log
    ADD COLUMN prompt_tokens INT DEFAULT NULL COMMENT '上游返回的输入 tokens' AFTER tokens_used,
    ADD COLUMN completion_tokens INT DEFAULT NULL COMMENT '上游返回的输出 tokens' AFTER prompt_tokens,
    ADD COLUMN cost_usd DECIMAL(16,8) DEFAULT NULL COMMENT '按调用时模型单价计算的 USD 成本' AFTER completion_tokens;
