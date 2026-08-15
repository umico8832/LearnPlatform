-- 阶段测评逐题快照固化题目知识点归属与名称，供复盘按知识点展示。
-- 与来源类别快照一致：只保存创建时的知识点事实，后续改关联不重写历史。
ALTER TABLE course_stage_assessment_question
    ADD COLUMN knowledge_points_json TEXT DEFAULT NULL COMMENT '题目知识点归属快照：[{"id":..,"name":".."}]' AFTER origin_question_id_snapshot;
