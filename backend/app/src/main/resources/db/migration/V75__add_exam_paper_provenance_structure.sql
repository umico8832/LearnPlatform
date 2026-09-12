-- 为官方试卷学习模式补齐可核验来源和原题结构；不在本迁移中导入任何试卷或题目。
ALTER TABLE `exam_paper`
  ADD COLUMN `paper_type` VARCHAR(30) NOT NULL DEFAULT 'PRACTICE'
    COMMENT '试卷类型：PRACTICE-普通练习 OFFICIAL_EXAM-官方原题' AFTER `create_by`,
  ADD COLUMN `exam_name` VARCHAR(200) DEFAULT NULL COMMENT '考试名称' AFTER `paper_type`,
  ADD COLUMN `exam_year` SMALLINT DEFAULT NULL COMMENT '考试年份' AFTER `exam_name`,
  ADD COLUMN `source_reference` VARCHAR(1000) DEFAULT NULL COMMENT '可核验的原始来源引用' AFTER `exam_year`,
  ADD COLUMN `source_verified` TINYINT NOT NULL DEFAULT 0 COMMENT '来源是否经管理员核验' AFTER `source_reference`,
  ADD KEY `idx_paper_type_year` (`paper_type`, `exam_year`);

ALTER TABLE `exam_question`
  ADD COLUMN `section_title` VARCHAR(200) DEFAULT NULL COMMENT '试卷分区或大题标题' AFTER `score`,
  ADD COLUMN `major_question_number` VARCHAR(30) DEFAULT NULL COMMENT '大题编号' AFTER `section_title`,
  ADD COLUMN `minor_question_number` VARCHAR(30) DEFAULT NULL COMMENT '小题编号' AFTER `major_question_number`,
  ADD COLUMN `subquestion_number` VARCHAR(30) DEFAULT NULL COMMENT '子问题编号' AFTER `minor_question_number`,
  ADD COLUMN `display_number` VARCHAR(100) DEFAULT NULL COMMENT '面向学习者展示的完整题号' AFTER `subquestion_number`,
  ADD KEY `idx_exam_display_number` (`exam_paper_id`, `display_number`);
