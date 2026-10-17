-- 动画标题按纯文本呈现，保留节点标识的代码标记在正文中，标题改用中文结构描述。
UPDATE question_understanding_content c JOIN question q ON q.id=c.question_id
SET c.solution_viz_title='先序先完成左子树再进入右子树'
WHERE q.source_reference='m5-draft:bt-03' AND c.content_version=1 AND c.review_status='PENDING';

UPDATE ai_variant_question v JOIN question_ai_asset a ON a.id=v.asset_id
JOIN question q ON q.id=a.question_id
SET v.viz_title='后序完成左右子树后访问子树根'
WHERE q.source_reference='m5-draft:bt-04' AND a.content='P27 二叉树待审核草稿变式' AND v.review_status='PENDING';
