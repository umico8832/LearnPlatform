ALTER TABLE `course_stage_assessment_question`
  ADD COLUMN `source_category_snapshot` VARCHAR(30) NOT NULL DEFAULT 'MANUAL'
    COMMENT 'OFFICIAL_EXAM/MANUAL/USER_PRIVATE/AI_GENERATED' AFTER `source_type_snapshot`;

UPDATE course_stage_assessment_question aq
JOIN question q ON q.id = aq.question_id
LEFT JOIN (
  SELECT DISTINCT eq.question_id
  FROM exam_question eq
  JOIN exam_paper ep ON ep.id = eq.exam_paper_id
  WHERE ep.paper_type = 'OFFICIAL_EXAM' AND ep.source_verified = 1
    AND ep.status = 1 AND ep.deleted = 0
) official ON official.question_id = q.id
SET aq.source_category_snapshot = CASE
  WHEN aq.source_type_snapshot = 'AI_GENERATED' THEN 'AI_GENERATED'
  WHEN q.visibility = 'PRIVATE' OR aq.source_type_snapshot = 'USER_PRIVATE_IMPORT' THEN 'USER_PRIVATE'
  WHEN official.question_id IS NOT NULL THEN 'OFFICIAL_EXAM'
  ELSE 'MANUAL'
END;
