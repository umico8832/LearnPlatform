CREATE INDEX idx_favorite_user_created
    ON user_favorite_question(user_id,create_time DESC,id DESC,question_id);

CREATE INDEX idx_submission_user_created
    ON question_submission(user_id,deleted,create_time DESC,id DESC);

CREATE INDEX idx_submission_user_filters
    ON question_submission(user_id,deleted,status,course_id,question_type,create_time DESC,id DESC);
