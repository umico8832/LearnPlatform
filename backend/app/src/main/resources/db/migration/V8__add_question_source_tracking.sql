-- 题目来源追踪和复审机制
-- 1. question 表增加来源和复审字段
ALTER TABLE `question`
  ADD COLUMN `source_type` VARCHAR(30) DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL-手动创建 SUBMISSION-投稿入库 EXCEL_IMPORT-Excel导入 MARKDOWN_IMPORT-Markdown导入 AI_GENERATED-AI生成' AFTER `create_by`,
  ADD COLUMN `source_reference` VARCHAR(500) DEFAULT NULL COMMENT '来源引用（投稿ID/导入批次ID等）' AFTER `source_type`,
  ADD COLUMN `last_review_time` DATETIME DEFAULT NULL COMMENT '最近复审时间' AFTER `source_reference`,
  ADD COLUMN `next_review_time` DATETIME DEFAULT NULL COMMENT '下次复审时间' AFTER `last_review_time`,
  ADD COLUMN `review_rounds` INT NOT NULL DEFAULT 0 COMMENT '累计复审轮次' AFTER `next_review_time`;

-- 为 source_type 和 next_review_time 添加索引
ALTER TABLE `question`
  ADD KEY `idx_source_type` (`source_type`),
  ADD KEY `idx_next_review_time` (`next_review_time`);

-- 2. 创建题目复审记录表
CREATE TABLE IF NOT EXISTS `question_review_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `reviewer_id` BIGINT NOT NULL COMMENT '复审人ID',
  `review_type` VARCHAR(30) NOT NULL COMMENT '复审类型：REGULAR-定期复审 TRIGGERED-触发复审 INITIAL-入库初审',
  `action` VARCHAR(30) NOT NULL COMMENT '复审动作：APPROVE-通过 REVISE-修订 REJECT-标记废弃',
  `old_content` TEXT DEFAULT NULL COMMENT '复审前题干（快照）',
  `new_content` TEXT DEFAULT NULL COMMENT '复审后题干（如有修订）',
  `old_difficulty` TINYINT DEFAULT NULL COMMENT '复审前难度',
  `new_difficulty` TINYINT DEFAULT NULL COMMENT '复审后难度（如有修订）',
  `comment` VARCHAR(1000) DEFAULT NULL COMMENT '复审意见',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '复审时间',
  PRIMARY KEY (`id`),
  KEY `idx_question_id` (`question_id`),
  KEY `idx_reviewer_id` (`reviewer_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题目复审记录表';