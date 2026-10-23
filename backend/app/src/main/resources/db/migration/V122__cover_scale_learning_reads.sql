CREATE INDEX idx_practice_user_question_correct
    ON practice_record(user_id,question_id,is_correct);

CREATE INDEX idx_question_course_listing
    ON question(course_id,deleted,status,visibility,create_time DESC,id DESC);
