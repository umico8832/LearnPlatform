-- 主观题自评没有服务端正确性和得分结论，以 NULL 保留“未判分”。
-- 历史 0/1 正确性及分数是已固化快照，不根据题型推测并回填。
ALTER TABLE exam_learning_answer
  MODIFY is_correct TINYINT NULL COMMENT '是否正确：0-错误 1-正确 NULL-未判分',
  MODIFY score INT NULL DEFAULT NULL COMMENT '本次得分；未判分为NULL';

ALTER TABLE exam_learning_ai_interaction
  MODIFY answer_correct TINYINT NULL COMMENT '最近一次作答是否正确快照；未判分为NULL';
