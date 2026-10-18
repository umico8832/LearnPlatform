DROP PROCEDURE IF EXISTS migrate_business_time_v118;
DELIMITER $$
CREATE PROCEDURE migrate_business_time_v118()
BEGIN
    DECLARE done BOOLEAN DEFAULT FALSE;
    DECLARE table_to_update VARCHAR(64);
    DECLARE assignments LONGTEXT;
    DECLARE previous_group_concat BIGINT;
    DECLARE tables_to_update CURSOR FOR
        SELECT table_name,
               GROUP_CONCAT(CONCAT('`', column_name, '`=CONVERT_TZ(`', column_name,
                   '`,\'Asia/Shanghai\',\'America/New_York\')') ORDER BY ordinal_position SEPARATOR ',')
        FROM information_schema.columns
        WHERE table_schema=DATABASE() AND data_type='datetime'
          AND table_name IN (
              'ai_asset_feedback','ai_asset_view','ai_call_log','ai_quota_audit_log','ai_usage_alert',
              'ai_variant_question','ai_variant_training','comment_like','community_attachment','community_category',
              'community_comment','community_like','community_post','community_question_link','community_review',
              'course','course_learning_event','course_stage_assessment','course_stage_assessment_question',
              'email_verification','exam_answer','exam_learning_ai_interaction','exam_learning_answer',
              'exam_learning_session','exam_paper','exam_question','exam_record','fsrs_review_event',
              'gamification_achievement','gamification_user_profile','gamification_xp_ledger','knowledge_content_bundle',
              'knowledge_content_chunk','knowledge_content_index','knowledge_point','learning_plan','oauth_login_ticket',
              'password_reset_token','practice_record','private_exam_draft_question','private_exam_import_draft',
              'question','question_ai_asset','question_ask_record','question_comment','question_correction_report',
              'question_knowledge_point','question_option','question_review_record','question_review_schedule',
              'question_submission','question_understanding_attempt','question_understanding_content',
              'question_understanding_hint','question_understanding_hint_receipt','question_understanding_misconception',
              'question_understanding_variant_attempt','question_understanding_viz_view','question_version',
              'subjective_grading_point','tutor_agent_message','tutor_agent_plan_confirmation','tutor_agent_practice_attempt',
              'tutor_agent_run','tutor_content','tutor_course_memory','tutor_session','tutor_session_note',
              'user','user_course','user_exam_source','user_favorite_question','user_identity','wrong_question')
          AND NOT (table_name='fsrs_review_event' AND column_name='reviewed_at')
          AND NOT (table_name='exam_record' AND column_name='start_instant_utc')
        GROUP BY table_name;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=TRUE;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF CONVERT_TZ('2026-01-01 12:00:00','Asia/Shanghai','America/New_York') IS NULL
       OR CONVERT_TZ('2026-07-01 12:00:00','Asia/Shanghai','America/New_York') <> '2026-07-01 00:00:00'
       OR CONVERT_TZ('2026-01-01 12:00:00','Asia/Shanghai','America/New_York') <> '2025-12-31 23:00:00' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V118 requires MySQL named timezone tables';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema=DATABASE() AND table_name='exam_record' AND column_name='start_instant_utc') THEN
        ALTER TABLE exam_record ADD COLUMN start_instant_utc DATETIME NULL;
    END IF;
    START TRANSACTION;
    UPDATE exam_record SET start_instant_utc=CONVERT_TZ(start_time,'Asia/Shanghai','+00:00'),
                           update_time=update_time;

    SET previous_group_concat=@@session.group_concat_max_len;
    SET SESSION group_concat_max_len=65535;
    OPEN tables_to_update;
    read_tables: LOOP
        FETCH tables_to_update INTO table_to_update, assignments;
        IF done THEN LEAVE read_tables; END IF;
        SET @business_time_update=CONCAT('UPDATE `',table_to_update,'` SET ',assignments);
        PREPARE statement_to_run FROM @business_time_update;
        EXECUTE statement_to_run;
        DEALLOCATE PREPARE statement_to_run;
    END LOOP;
    CLOSE tables_to_update;
    SET SESSION group_concat_max_len=previous_group_concat;

    UPDATE gamification_xp_ledger SET event_date=DATE(occurred_time);
    WITH days AS (
        SELECT DISTINCT user_id,event_date FROM gamification_xp_ledger WHERE xp>0
    ), islands AS (
        SELECT user_id,event_date,
               DATE_SUB(event_date, INTERVAL ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY event_date) DAY) grp
        FROM days
    ), streaks AS (
        SELECT user_id,MAX(event_date) last_day,COUNT(*) streak FROM islands GROUP BY user_id,grp
    ), ranked AS (
        SELECT user_id,last_day,streak,MAX(streak) OVER (PARTITION BY user_id) max_streak,
               ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY last_day DESC) position FROM streaks
    )
    UPDATE gamification_user_profile p JOIN ranked r ON p.user_id=r.user_id AND r.position=1
    SET p.last_answer_date=r.last_day,p.current_streak=r.streak,p.max_streak=r.max_streak,
        p.version=p.version+1,p.update_time=p.update_time;
    COMMIT;
END$$
DELIMITER ;
CALL migrate_business_time_v118();
DROP PROCEDURE migrate_business_time_v118;

SET time_zone='America/New_York';
