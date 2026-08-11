-- 试卷学习模式与限时考试分离；会话和逐题尝试均保存为独立业务事实。
CREATE TABLE `exam_learning_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '试卷学习会话ID',
  `user_id` BIGINT NOT NULL COMMENT '学习用户ID',
  `exam_paper_id` BIGINT NOT NULL COMMENT '已发布试卷ID',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-进行中 1-已完成',
  `current_question_id` BIGINT DEFAULT NULL COMMENT '最近一次服务端确认的学习位置',
  `active_session_key` VARCHAR(160) DEFAULT NULL COMMENT '进行中会话幂等键，完成后置空',
  `start_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `complete_time` DATETIME DEFAULT NULL COMMENT '完成时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_learning_active_session` (`active_session_key`),
  KEY `idx_exam_learning_user_paper` (`user_id`, `exam_paper_id`, `create_time`),
  KEY `idx_exam_learning_paper` (`exam_paper_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷学习会话';

CREATE TABLE `exam_learning_answer` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '试卷学习逐题作答ID',
  `session_id` BIGINT NOT NULL COMMENT '试卷学习会话ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `attempt_no` INT NOT NULL COMMENT '本会话内该题尝试序号',
  `user_answer` VARCHAR(1000) NOT NULL COMMENT '用户答案',
  `is_correct` TINYINT NOT NULL COMMENT '是否正确：0-错误 1-正确',
  `score` INT NOT NULL DEFAULT 0 COMMENT '本次得分',
  `answer_time` INT DEFAULT NULL COMMENT '本次答题耗时（秒）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作答时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_learning_question_attempt` (`session_id`, `question_id`, `attempt_no`),
  KEY `idx_exam_learning_answer_session` (`session_id`, `create_time`),
  KEY `idx_exam_learning_answer_question` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷学习逐题作答';
