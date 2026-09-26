CREATE TABLE tutor_course_memory (
  user_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  revision BIGINT NOT NULL,
  explanation_style VARCHAR(32) NULL,
  goal VARCHAR(500) NULL,
  PRIMARY KEY (user_id, course_id),
  CONSTRAINT chk_tutor_memory_revision CHECK (revision >= 1),
  CONSTRAINT chk_tutor_memory_style CHECK (
    explanation_style IS NULL OR explanation_style IN ('STEP_BY_STEP', 'CONCISE', 'EXAMPLES'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户主动保存的课程学习偏好和目标';
