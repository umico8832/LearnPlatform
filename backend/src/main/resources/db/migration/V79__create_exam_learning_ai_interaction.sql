-- 试卷学习中的 AI 辅导必须绑定本人会话、原试卷题目和最近一次真实作答。
-- 不保存原始 prompt 或 AI 输出；内容由现有 AI 调用日志以不可逆指纹和用量元数据审计。
CREATE TABLE exam_learning_ai_interaction (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'AI 辅导交互ID',
  user_id BIGINT NOT NULL COMMENT '学习用户ID',
  course_id BIGINT NOT NULL COMMENT '课程ID',
  exam_paper_id BIGINT NOT NULL COMMENT '原试卷ID',
  learning_session_id BIGINT NOT NULL COMMENT '试卷学习会话ID',
  question_id BIGINT NOT NULL COMMENT '原试卷题目ID',
  answer_id BIGINT NOT NULL COMMENT '发起辅导时最近一次试卷学习作答ID',
  answer_attempt_no INT NOT NULL COMMENT '最近一次作答序号快照',
  answer_correct TINYINT NOT NULL COMMENT '最近一次作答是否正确快照',
  interaction_type VARCHAR(30) NOT NULL COMMENT 'EXPLANATION-讲解 VARIANT-同知识点练习',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0-处理中 1-成功 2-失败',
  error_message VARCHAR(500) DEFAULT NULL COMMENT '失败摘要，不保存 prompt 或 AI 输出',
  start_time DATETIME NOT NULL COMMENT '调用开始时间',
  complete_time DATETIME DEFAULT NULL COMMENT '调用完成时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (id),
  KEY idx_exam_learning_ai_session_time (learning_session_id, create_time),
  KEY idx_exam_learning_ai_user_course_time (user_id, course_id, create_time),
  KEY idx_exam_learning_ai_question (question_id, create_time),
  KEY idx_exam_learning_ai_answer (answer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷学习AI辅导交互';
