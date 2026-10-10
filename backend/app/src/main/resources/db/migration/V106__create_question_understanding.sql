CREATE TABLE question_understanding_content (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  question_id BIGINT NOT NULL,
  content_version INT NOT NULL,
  review_status VARCHAR(20) NOT NULL,
  reviewed_by VARCHAR(100) NULL,
  reviewed_time DATETIME NULL,
  question_fingerprint CHAR(64) NOT NULL,
  knowledge_point_id BIGINT NOT NULL,
  solution_explanation TEXT NOT NULL,
  solution_viz_title VARCHAR(200) NULL,
  solution_viz_spec_json JSON NULL,
  source_exam_question_id BIGINT NULL,
  source_title VARCHAR(300) NULL,
  source_url VARCHAR(1000) NULL,
  source_verification VARCHAR(20) NOT NULL,
  source_verified_by VARCHAR(100) NULL,
  source_verified_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_understanding_content_version (question_id, content_version),
  KEY idx_understanding_content_question (question_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE question_understanding_misconception (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  content_id BIGINT NOT NULL,
  option_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  explanation TEXT NOT NULL,
  content_source VARCHAR(20) NOT NULL,
  viz_title VARCHAR(200) NULL,
  viz_spec_json JSON NULL,
  UNIQUE KEY uk_understanding_misconception_option (content_id, option_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE question_understanding_attempt (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  content_id BIGINT NOT NULL,
  variant_id BIGINT NULL,
  practice_record_id BIGINT NOT NULL,
  solution_revealed_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_understanding_practice_record (practice_record_id),
  KEY idx_understanding_attempt_owner (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE question_understanding_variant_attempt (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  origin_attempt_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  variant_id BIGINT NOT NULL,
  user_answer VARCHAR(100) NOT NULL,
  is_correct TINYINT NOT NULL,
  assistance VARCHAR(20) NOT NULL,
  answer_time INT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_understanding_variant_origin (origin_attempt_id),
  KEY idx_understanding_variant_owner (user_id, variant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

ALTER TABLE ai_variant_question
  ADD COLUMN changed_condition VARCHAR(500) NULL,
  ADD COLUMN viz_title VARCHAR(200) NULL,
  ADD COLUMN viz_spec_json JSON NULL;
