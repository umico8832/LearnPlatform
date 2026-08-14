CREATE TABLE `private_exam_import_draft` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '私有试卷导入草稿ID',
  `owner_user_id` BIGINT NOT NULL COMMENT '草稿所有者',
  `title` VARCHAR(200) NOT NULL COMMENT '试卷标题',
  `course_id` BIGINT NOT NULL COMMENT '所属课程ID',
  `duration` INT NOT NULL DEFAULT 60 COMMENT '考试时长（分钟）',
  `source_record_id` BIGINT NOT NULL COMMENT '用户原始资料记录ID',
  `status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/AI_GENERATED/REVIEWING/READY/CONFIRMED',
  `confirmed_paper_id` BIGINT DEFAULT NULL COMMENT '确认启用后的私有试卷ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_private_exam_draft_owner_status` (`owner_user_id`, `status`, `update_time`),
  KEY `idx_private_exam_draft_source` (`source_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私有试卷答案复核草稿';

CREATE TABLE `private_exam_draft_question` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '草稿题目ID',
  `draft_id` BIGINT NOT NULL COMMENT '导入草稿ID',
  `sort_order` INT NOT NULL COMMENT '原始题序',
  `content` TEXT NOT NULL COMMENT '题干',
  `question_type` VARCHAR(30) NOT NULL COMMENT 'SINGLE_CHOICE/MULTIPLE_CHOICE/TRUE_FALSE',
  `score` INT NOT NULL DEFAULT 1 COMMENT '题目分值',
  `options_json` TEXT NOT NULL COMMENT '仅含标签与正文的选项JSON',
  `original_answer_json` VARCHAR(500) DEFAULT NULL COMMENT '原资料答案标签JSON',
  `original_analysis` TEXT DEFAULT NULL COMMENT '原资料解析',
  `ai_answer_json` VARCHAR(500) DEFAULT NULL COMMENT 'AI建议答案标签JSON',
  `ai_analysis` TEXT DEFAULT NULL COMMENT 'AI建议解析',
  `generation_status` VARCHAR(24) NOT NULL COMMENT 'NOT_REQUIRED/PENDING/GENERATED',
  `final_answer_json` VARCHAR(500) DEFAULT NULL COMMENT '用户复核后的答案标签JSON',
  `final_analysis` TEXT DEFAULT NULL COMMENT '用户复核后的解析',
  `review_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/REVIEWED',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_private_exam_draft_sort` (`draft_id`, `sort_order`),
  KEY `idx_private_exam_draft_question_status` (`draft_id`, `review_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私有试卷草稿逐题答案复核';
