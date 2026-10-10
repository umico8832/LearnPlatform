ALTER TABLE question_review_schedule
  MODIFY COLUMN ease_factor DECIMAL(4,2) NULL DEFAULT 2.50,
  ADD COLUMN fsrs_card_json JSON NULL,
  ADD COLUMN fsrs_last_rating VARCHAR(10) NULL,
  ADD COLUMN fsrs_version SMALLINT NULL;

CREATE TABLE fsrs_review_event (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  schedule_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  source_type VARCHAR(30) NOT NULL,
  source_id BIGINT NOT NULL,
  rating VARCHAR(10) NOT NULL,
  reviewed_at DATETIME NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_fsrs_review_source (source_type, source_id),
  KEY idx_fsrs_review_user_question (user_id, question_id, reviewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
