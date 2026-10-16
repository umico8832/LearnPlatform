SET @b2_tree_content = (
  SELECT id FROM question_understanding_content
  WHERE source_title='2026 年 408 真题 第 3 题' AND content_version=1 LIMIT 1
);
SET @b2_stack_content = (
  SELECT id FROM question_understanding_content
  WHERE source_title='系统种子生成：合法出栈序列' AND content_version=1 LIMIT 1
);

-- 冷读与脚本校验不构成人工审核；这批 AI 内容等待页面审核及来源核验。
UPDATE question_understanding_content
SET review_status='PENDING', reviewed_by=NULL, reviewed_time=NULL,
    solution_content_source='AI_GENERATED', source_verification='PENDING',
    source_verified_by=NULL, source_verified_time=NULL
WHERE id IN (@b2_tree_content,@b2_stack_content);

UPDATE question_understanding_misconception
SET content_source='AI_GENERATED'
WHERE content_id IN (@b2_tree_content,@b2_stack_content);

UPDATE question_understanding_hint
SET content_source='AI_GENERATED', review_status='PENDING', reviewed_by=NULL, reviewed_time=NULL
WHERE content_id IN (@b2_tree_content,@b2_stack_content);

UPDATE question_understanding_hint
SET hint_text='例如按甲、乙、丙入栈，为让乙先出栈，先压入甲、乙，弹出乙。接着要取丙时继续压入丙，再核对栈顶。对本题每个候选也逐步记录待入栈元素和栈顶。'
WHERE content_id=@b2_stack_content AND hint_level=3;

UPDATE ai_variant_question v
JOIN question_ai_asset a ON a.id=v.asset_id AND a.asset_type='VARIANT'
JOIN question_understanding_content c ON c.question_id=a.question_id
SET v.review_status='PENDING', v.review_note=NULL, v.reviewed_by=NULL, v.reviewed_time=NULL
WHERE c.id IN (@b2_tree_content,@b2_stack_content)
  AND a.content='题目理解系统种子变式' AND v.changed_condition IS NOT NULL;

UPDATE ai_variant_question v
JOIN question_ai_asset a ON a.id=v.asset_id AND a.asset_type='VARIANT'
JOIN question_understanding_content c ON c.question_id=a.question_id
SET v.changed_condition='中序由 b, e, d, f, c, a, g 改为 c, b, e, d, f, a, g；层序序列不变，仍求后序遍历；中序变化使树形与后序结果改变。'
WHERE c.id=@b2_tree_content AND a.content='题目理解系统种子变式'
  AND v.changed_condition IS NOT NULL;
