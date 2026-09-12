-- 结构化 AI 变式题：标准答案只保存在服务端，用户提交后由统一判分器判分

CREATE TABLE `ai_variant_question` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asset_id` BIGINT NOT NULL COMMENT '对应的变式题学习资产ID',
  `question_type` VARCHAR(30) NOT NULL COMMENT '题型，首版固定为 SINGLE_CHOICE',
  `question_content` TEXT NOT NULL COMMENT '变式题题干',
  `options_json` TEXT NOT NULL COMMENT '不含正确性标记的选项 JSON',
  `correct_answer` VARCHAR(100) NOT NULL COMMENT '服务端判分使用的正确答案',
  `analysis` TEXT NOT NULL COMMENT '提交后展示的解析',
  `difficulty` TINYINT NOT NULL DEFAULT 3 COMMENT '难度等级：1-5',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_id` (`asset_id`),
  KEY `idx_question_type` (`question_type`),
  KEY `idx_difficulty` (`difficulty`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='结构化 AI 变式题';

ALTER TABLE `ai_variant_training`
  ADD COLUMN `user_answer` VARCHAR(500) NULL COMMENT '首次提交答案' AFTER `status`,
  ADD COLUMN `is_correct` TINYINT NULL COMMENT '首次提交是否正确：1-正确 0-错误' AFTER `user_answer`,
  ADD COLUMN `answered_time` DATETIME NULL COMMENT '首次判分时间' AFTER `is_correct`,
  ADD KEY `idx_answered_time` (`answered_time`),
  ADD KEY `idx_answer_result` (`is_correct`, `answered_time`);
