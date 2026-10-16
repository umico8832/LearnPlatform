-- 仅隔离 E2E 加载的已审核状态夹具，复用集成测试的准备方式。
-- 生产迁移中的待审标记保持原样；此夹具不构成人工内容审核。
SET @e2e_stack_content = (
  SELECT id FROM question_understanding_content
  WHERE source_title='系统种子生成：合法出栈序列' AND content_version=1 LIMIT 1
);

UPDATE question_understanding_content
SET review_status='REVIEWED', reviewed_by='e2e-fixture', reviewed_time=CURRENT_TIMESTAMP
WHERE id=@e2e_stack_content;

UPDATE ai_variant_question v
JOIN question_ai_asset a ON a.id=v.asset_id AND a.asset_type='VARIANT'
JOIN question_understanding_content c ON c.question_id=a.question_id
SET v.review_status='APPROVED', v.reviewed_by=0, v.reviewed_time=CURRENT_TIMESTAMP,
    v.review_note='Isolated E2E fixture; not a production content review'
WHERE c.id=@e2e_stack_content AND a.content='题目理解系统种子变式'
  AND v.changed_condition IS NOT NULL;
