-- Phase 13: AI 学习资产质量反馈表
-- 用户对 AI 生成的学习资产内容给出有帮助/无帮助反馈

CREATE TABLE `ai_asset_feedback` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `asset_type` VARCHAR(50) NOT NULL COMMENT '资产类型',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `helpful` TINYINT NOT NULL COMMENT '是否有帮助：1-有帮助 0-无帮助',
  `comment` VARCHAR(500) DEFAULT NULL COMMENT '用户补充反馈文字（可选）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_user` (`question_id`, `asset_type`, `user_id`),
  KEY `idx_question_asset` (`question_id`, `asset_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 学习资产质量反馈表';