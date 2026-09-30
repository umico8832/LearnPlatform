CREATE TABLE gamification_user_profile (
    user_id BIGINT NOT NULL,
    daily_goal INT NOT NULL DEFAULT 10,
    total_xp BIGINT NOT NULL DEFAULT 0,
    answered_count BIGINT NOT NULL DEFAULT 0,
    correct_count BIGINT NOT NULL DEFAULT 0,
    review_count BIGINT NOT NULL DEFAULT 0,
    tutor_count BIGINT NOT NULL DEFAULT 0,
    stage_count BIGINT NOT NULL DEFAULT 0,
    current_combo INT NOT NULL DEFAULT 0,
    max_combo INT NOT NULL DEFAULT 0,
    current_streak INT NOT NULL DEFAULT 0,
    max_streak INT NOT NULL DEFAULT 0,
    last_answer_date DATE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    last_event_id BIGINT NOT NULL DEFAULT 0,
    backfill_cursor BIGINT NOT NULL DEFAULT 0,
    backfill_complete BOOLEAN NOT NULL DEFAULT FALSE,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT chk_gamification_daily_goal CHECK (daily_goal BETWEEN 1 AND 200),
    CONSTRAINT chk_gamification_total_xp CHECK (total_xp >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户学习投入奖励概况';

CREATE TABLE gamification_xp_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    subject_type VARCHAR(40) NOT NULL,
    subject_id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    event_date DATE NOT NULL,
    xp INT NOT NULL,
    reward_reason VARCHAR(48) NOT NULL,
    occurred_time DATETIME NOT NULL,
    total_xp_before BIGINT NOT NULL DEFAULT 0,
    total_xp_after BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gamification_xp_event (event_id),
    KEY idx_gamification_xp_user_date (user_id, event_date),
    KEY idx_gamification_xp_user_subject_date (user_id, course_id, subject_type, subject_id, event_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习投入经验流水，唯一关联课程学习事件';

CREATE TABLE gamification_achievement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    achievement_code VARCHAR(64) NOT NULL,
    unlocked_at DATETIME NOT NULL,
    trigger_event_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gamification_achievement_user_code (user_id, achievement_code),
    KEY idx_gamification_achievement_user_time (user_id, unlocked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='由真实学习事实解锁的成就';
