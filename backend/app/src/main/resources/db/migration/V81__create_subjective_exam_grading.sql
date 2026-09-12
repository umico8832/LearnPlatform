-- 主观题不再沿用 SHORT_ANSWER 关键词命中自动判分。
-- 考生提交后进入待批阅状态，评分点由管理员逐项给分，最终成绩在全部主观题完成复核后固化。

ALTER TABLE exam_answer
  ADD COLUMN grading_status VARCHAR(20) NOT NULL DEFAULT 'AUTO_GRADED' COMMENT 'AUTO_GRADED/PENDING/REVIEWED' AFTER score,
  ADD COLUMN reviewer_id BIGINT DEFAULT NULL COMMENT '人工批阅管理员ID' AFTER grading_status,
  ADD COLUMN review_comment VARCHAR(1000) DEFAULT NULL COMMENT '人工批阅总评' AFTER reviewer_id,
  ADD COLUMN review_detail_json JSON DEFAULT NULL COMMENT '逐评分点得分与评语' AFTER review_comment,
  ADD COLUMN reviewed_at DATETIME DEFAULT NULL COMMENT '人工批阅时间' AFTER review_detail_json,
  ADD KEY idx_exam_answer_grading_status (grading_status);

CREATE TABLE subjective_grading_point (
  id BIGINT NOT NULL AUTO_INCREMENT,
  question_id BIGINT NOT NULL,
  point_key VARCHAR(50) NOT NULL,
  title VARCHAR(200) NOT NULL,
  description TEXT NOT NULL,
  reference_answer TEXT NOT NULL,
  max_score INT NOT NULL,
  sort_order INT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_subjective_question_point (question_id, point_key),
  KEY idx_subjective_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='主观题人工批阅评分点';
