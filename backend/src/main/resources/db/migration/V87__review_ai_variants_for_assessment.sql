ALTER TABLE `question`
  ADD COLUMN `origin_question_id` BIGINT DEFAULT NULL COMMENT 'AI生成题等内容的母题ID' AFTER `source_reference`,
  ADD KEY `idx_question_origin` (`origin_question_id`);

ALTER TABLE `ai_variant_question`
  ADD COLUMN `review_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED' AFTER `difficulty`,
  ADD COLUMN `review_note` VARCHAR(500) DEFAULT NULL AFTER `review_status`,
  ADD COLUMN `reviewed_by` BIGINT DEFAULT NULL AFTER `review_note`,
  ADD COLUMN `reviewed_time` DATETIME DEFAULT NULL AFTER `reviewed_by`,
  ADD COLUMN `published_question_id` BIGINT DEFAULT NULL AFTER `reviewed_time`,
  ADD UNIQUE KEY `uk_ai_variant_published_question` (`published_question_id`),
  ADD KEY `idx_ai_variant_review_status` (`review_status`, `create_time`);

ALTER TABLE `course_stage_assessment_question`
  ADD COLUMN `source_type_snapshot` VARCHAR(30) NOT NULL DEFAULT 'MANUAL' AFTER `question_type`,
  ADD COLUMN `origin_question_id_snapshot` BIGINT DEFAULT NULL AFTER `source_type_snapshot`;
