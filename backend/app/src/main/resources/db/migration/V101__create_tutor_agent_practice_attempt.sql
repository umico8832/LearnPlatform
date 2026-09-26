CREATE TABLE tutor_agent_practice_attempt (
  id BIGINT NOT NULL AUTO_INCREMENT,
  message_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  practice_record_id BIGINT NOT NULL,
  question_json JSON NOT NULL,
  result_json JSON NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tutor_agent_practice_message (message_id),
  UNIQUE KEY uk_tutor_agent_practice_record (practice_record_id),
  KEY idx_tutor_agent_practice_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Tutor Agent 推荐变式题的首次真实作答';
