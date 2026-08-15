ALTER TABLE course_stage_assessment
    ADD COLUMN target_knowledge_point_id BIGINT DEFAULT NULL AFTER selection_strategy,
    ADD COLUMN target_knowledge_point_name_snapshot VARCHAR(255) DEFAULT NULL AFTER target_knowledge_point_id,
    ADD KEY idx_course_stage_assessment_target (target_knowledge_point_id);
