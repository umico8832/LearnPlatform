ALTER TABLE `exam_paper`
  ADD COLUMN `owner_user_id` BIGINT DEFAULT NULL COMMENT '私有试卷所有者；公共试卷为空' AFTER `create_by`,
  ADD COLUMN `visibility` VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性：PUBLIC/PRIVATE' AFTER `owner_user_id`,
  ADD COLUMN `source_record_id` BIGINT DEFAULT NULL COMMENT '用户原始资料记录ID' AFTER `source_verified`,
  ADD COLUMN `import_status` VARCHAR(20) DEFAULT NULL COMMENT '用户导入状态：CONFIRMED' AFTER `source_record_id`,
  ADD KEY `idx_exam_visibility_owner_status` (`visibility`, `owner_user_id`, `status`),
  ADD KEY `idx_exam_source_record` (`source_record_id`);

ALTER TABLE `question`
  ADD COLUMN `owner_user_id` BIGINT DEFAULT NULL COMMENT '私有题目所有者；公共题目为空' AFTER `create_by`,
  ADD COLUMN `visibility` VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性：PUBLIC/PRIVATE' AFTER `owner_user_id`,
  ADD KEY `idx_question_visibility_owner_status` (`visibility`, `owner_user_id`, `status`);

CREATE TABLE `user_exam_source` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '原始资料ID',
  `owner_user_id` BIGINT NOT NULL COMMENT '资料所有者',
  `source_name` VARCHAR(255) NOT NULL COMMENT '用户提供的原始资料名称',
  `source_format` VARCHAR(20) NOT NULL COMMENT 'MARKDOWN/TEXT',
  `content_sha256` CHAR(64) NOT NULL COMMENT '原始内容SHA-256',
  `original_content` MEDIUMTEXT NOT NULL COMMENT '用户确认导入的原始文本',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_exam_source_owner_time` (`owner_user_id`, `create_time`),
  KEY `idx_user_exam_source_hash` (`owner_user_id`, `content_sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户私有试卷原始资料';
