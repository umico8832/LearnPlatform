CREATE TABLE `course_stage_assessment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `course_id` BIGINT NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
  `selection_strategy` VARCHAR(40) NOT NULL,
  `question_count` INT NOT NULL,
  `correct_count` INT DEFAULT NULL,
  `active_session_key` VARCHAR(16) DEFAULT 'ACTIVE',
  `start_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `complete_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_stage_assessment_active` (`user_id`, `course_id`, `active_session_key`),
  KEY `idx_course_stage_assessment_history` (`user_id`, `course_id`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程阶段测评会话';

CREATE TABLE `course_stage_assessment_question` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `assessment_id` BIGINT NOT NULL,
  `question_id` BIGINT NOT NULL,
  `sort_order` INT NOT NULL,
  `question_type` VARCHAR(30) NOT NULL,
  `content_snapshot` TEXT NOT NULL,
  `options_snapshot` JSON NOT NULL,
  `correct_answer_snapshot` VARCHAR(500) NOT NULL,
  `analysis_snapshot` TEXT DEFAULT NULL,
  `score` INT NOT NULL,
  `user_answer` VARCHAR(500) DEFAULT NULL,
  `is_correct` TINYINT DEFAULT NULL,
  `answered_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_stage_assessment_question` (`assessment_id`, `question_id`),
  UNIQUE KEY `uk_course_stage_assessment_sort` (`assessment_id`, `sort_order`),
  KEY `idx_course_stage_assessment_original_question` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程阶段测评题目快照与作答';
