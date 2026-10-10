-- 逐题核对 https://csgraduates.com/study_methods/408quiz/2026/：第 3 题的题号、选项、答案一致；题干标点与问法按本站单选格式统一。
SET @tree_q = (
  SELECT e.question_id FROM exam_question e JOIN exam_paper p ON p.id=e.exam_paper_id
  WHERE p.exam_year=2026 AND p.paper_type='OFFICIAL_EXAM' AND p.source_verified=1
    AND e.display_number='第3题' AND p.deleted=0 AND p.status=1 LIMIT 1
);
SET @tree_eq = (SELECT id FROM exam_question WHERE question_id=@tree_q AND display_number='第3题' LIMIT 1);
SET @tree_kp = (SELECT id FROM knowledge_point WHERE content_key='408-trees' AND deleted=0 LIMIT 1);
SET @tree_fp = (
  SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
    (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
      ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@tree_q
);
INSERT INTO question_understanding_content
(question_id,content_version,review_status,reviewed_by,reviewed_time,question_fingerprint,knowledge_point_id,
 solution_explanation,solution_viz_title,solution_viz_spec_json,source_exam_question_id,source_title,source_url,
 source_verification,source_verified_by,source_verified_time)
VALUES
(@tree_q,1,'REVIEWED','system-seed',CURRENT_TIMESTAMP,@tree_fp,@tree_kp,
 '1. 层序第一个是根 **a**。中序以 a 为界：左子树是 b, e, d, f, c，右子树只有 g。\n2. 左子树里，层序最先出现的是 b，它是这一段的根。b 在这段中序的最前面，所以 **b 没有左子树**，右子树是 e, d, f, c。\n3. 在 e, d, f, c 里，层序最先出现的是 c。c 在中序的最后，所以 **c 只有左子树** e, d, f。\n4. 在 e, d, f 里，层序先出现 d，所以 d 是根，e、f 分别是它的左、右孩子。\n5. 后序按“左、右、根”：先输出左子树 e, f, d, c, b，再输出右子树 g，最后输出根 a。答案是 C。',
 '用本题的树走一遍后序',CAST('{"scene":"binary-tree","op":"postorder","tree":["a","b","g",null,"c",null,null,"d",null,"e","f"],"pauseAt":[]}' AS JSON),
 @tree_eq,'2026 年 408 真题 第 3 题','https://csgraduates.com/study_methods/408quiz/2026/',
 'VERIFIED','codex-check, claude-review',CURRENT_TIMESTAMP);
SET @tree_content = LAST_INSERT_ID();
INSERT INTO question_understanding_misconception
(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @tree_content,id,
 CASE option_label WHEN 'A' THEN 'c 放错了边，d 的子树又按中序写' WHEN 'B' THEN '把 c 放成了 b 的左孩子' ELSE '让 g 插进了左子树中间' END,
 CASE option_label
  WHEN 'A' THEN 'A 里有两处不是后序。第一，**c 被最先输出**：中序 b, e, d, f, c 里 b 排在最前，说明 b 没有左子树，c 在 b 的右边，而且 c 下面还挂着 d、e、f，不可能第一个输出。第二，**e, d, f 是中序的顺序**：d 的两个孩子都要先输出，d 应该排在 e、f 之后。'
  WHEN 'B' THEN '层序里 c 排在 b、g 之后，它确实是 b 的孩子，但在左边还是右边要看中序：中序 b, e, d, f, c 中 b 排在这一段最前面，说明 **b 没有左子树**，c 只能在 b 的右边，而且 d、e、f 都在 c 的左子树里。把 c 当成 b 左边的叶子，后序才会先输出 c。'
  ELSE '中序 b, e, d, f, c, **a**, g 中 g 在 a 的右边，是右子树。后序先走完整棵左子树（b、c、d、e、f），再走右子树 g，最后才是 a，所以 g 只能排在倒数第二。D 在 e 之后就输出 g，还把 c 排到了 b 后面，可 c 是 b 的后代，应该先于 b 输出。' END,
 'CURATED',CASE option_label WHEN 'D' THEN '看后序真正的输出顺序' ELSE '用本题的树走一遍后序，看谁先输出' END,
 CASE option_label WHEN 'D' THEN CAST('{"scene":"binary-tree","op":"postorder","tree":["a","b","g",null,"c",null,null,"d",null,"e","f"],"pauseAt":[]}' AS JSON)
 ELSE CAST('{"scene":"binary-tree","op":"postorder","tree":["a","b","g",null,"c",null,null,"d",null,"e","f"],"pauseAt":[{"id":"tree-first-visit","guess":"c"}]}' AS JSON) END
FROM question_option WHERE question_id=@tree_q AND deleted=0 AND is_correct=0;

SET @stack_course = (SELECT id FROM course WHERE content_key='cs408-data-structures' AND deleted=0 LIMIT 1);
SET @stack_kp = (SELECT id FROM knowledge_point WHERE content_key='cs408-stack-pop-sequences' AND deleted=0 LIMIT 1);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,visibility)
VALUES('入栈顺序为 1,2,3,4,5，下列哪个是可能的出栈序列？','SINGLE_CHOICE',@stack_course,3,
 '按入栈顺序模拟。选项 A：压入 1,2,3 后依次弹出 3,2,1；再压入 4,5，依次弹出 5,4，整个序列可达。其余选项都会要求弹出被栈顶元素压住的数。',
 '栈,合法出栈序列,理解检查',2,1,0,0,'AI_GENERATED','PUBLIC');
SET @stack_q = LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@stack_q,'3,2,1,5,4','A',1,1),(@stack_q,'3,1,2,5,4','B',0,2),
(@stack_q,'4,2,3,1,5','C',0,3),(@stack_q,'2,5,3,4,1','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@stack_q,@stack_kp);
SET @stack_fp = (
  SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
    (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
      ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@stack_q
);
INSERT INTO question_understanding_content
(question_id,content_version,review_status,reviewed_by,reviewed_time,question_fingerprint,knowledge_point_id,
 solution_explanation,solution_viz_title,solution_viz_spec_json,source_title,source_verification)
VALUES(@stack_q,1,'REVIEWED','system-seed',CURRENT_TIMESTAMP,@stack_fp,@stack_kp,
 '固定按 1 到 5 入栈，只能弹出当前栈顶。要得到 3,2,1，先把 1、2、3 依次压入，再连续弹出 3、2、1。接着把 4、5 压入，弹出 5、4，恰好得到选项 A，且每一步都从栈顶取数。',
 '正确选项 3,2,1,5,4：每一步都取栈顶',CAST('{"scene":"stack","op":"check","seq":[3,2,1,5,4],"pauseAt":[]}' AS JSON),
 '系统种子生成：合法出栈序列','REVIEWED');
SET @stack_content = LAST_INSERT_ID();
INSERT INTO question_understanding_misconception
(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @stack_content,id,
 CASE option_label WHEN 'B' THEN '以为能越过 2 直接弹出 1' WHEN 'C' THEN '以为能越过 3 直接弹出 2' ELSE '以为能越过 4 直接弹出 3' END,
 CASE option_label
  WHEN 'B' THEN '你可能以为弹出 3 后，栈里的 1 就可以接着弹出。此时栈顶是 2，1 被压在下面；序列要求下一步取 1，栈不能越过 2。'
  WHEN 'C' THEN '你可能以为弹出 4 后，可以在栈中任选 2。此时 3 仍压在 2 上面；序列要求下一步取 2，无法从栈顶完成。'
  ELSE '你可能以为弹出 5 后，就能从栈里取出 3。此时栈顶是 4，3 被压在下面；序列要求下一步取 3，不能跳过 4。' END,
 'CURATED',CASE option_label WHEN 'B' THEN '用你选的 3,1,2,5,4 走一遍'
 WHEN 'C' THEN '用你选的 4,2,3,1,5 走一遍' ELSE '用你选的 2,5,3,4,1 走一遍' END,
 CASE option_label WHEN 'B' THEN CAST('{"scene":"stack","op":"check","seq":[3,1,2,5,4],"pauseAt":["stack-buried"]}' AS JSON)
 WHEN 'C' THEN CAST('{"scene":"stack","op":"check","seq":[4,2,3,1,5],"pauseAt":["stack-buried"]}' AS JSON)
 ELSE CAST('{"scene":"stack","op":"check","seq":[2,5,3,4,1],"pauseAt":["stack-buried"]}' AS JSON) END
FROM question_option WHERE question_id=@stack_q AND deleted=0 AND is_correct=0;

INSERT INTO question_ai_asset(question_id,asset_type,content,model,deleted)
VALUES(@tree_q,'VARIANT','题目理解系统种子变式',NULL,0);
SET @tree_asset = LAST_INSERT_ID();
INSERT INTO ai_variant_question
(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,
 review_note,reviewed_by,reviewed_time,changed_condition,viz_title,viz_spec_json)
VALUES(@tree_asset,'SINGLE_CHOICE',
 '已知二叉树 T 的中序遍历为 c, b, e, d, f, a, g，层序遍历为 a, b, g, c, d, e, f，则其后序遍历序列为（ ）。',
 JSON_ARRAY(JSON_OBJECT('label','A','content','e, f, d, c, b, g, a'),JSON_OBJECT('label','B','content','c, e, f, d, b, g, a'),JSON_OBJECT('label','C','content','c, e, d, f, b, g, a'),JSON_OBJECT('label','D','content','e, f, d, b, c, g, a')),
 'B','层序没变，根仍是 a，左子树的根仍是 b。但新中序里 **c 排在 b 前面**，说明 c 是 b 的左孩子；e, d, f 在 b 的右边，其中 d 是根，e、f 是它的左右孩子。后序：c, e, f, d, b, g, a，选 B。和原题对照：同样的层序，c 在 b 的左边还是右边，由中序决定。',
 3,'APPROVED','Claude 内容复核通过',0,CURRENT_TIMESTAMP,
 '中序由 b, e, d, f, c, a, g 改为 c, b, e, d, f, a, g；层序和所求的后序都不变。',
 '新树的后序',CAST('{"scene":"binary-tree","op":"postorder","tree":["a","b","g","c","d",null,null,null,null,"e","f"],"pauseAt":[]}' AS JSON));

INSERT INTO question_ai_asset(question_id,asset_type,content,model,deleted)
VALUES(@stack_q,'VARIANT','题目理解系统种子变式',NULL,0);
SET @stack_asset = LAST_INSERT_ID();
INSERT INTO ai_variant_question
(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,
 review_note,reviewed_by,reviewed_time,changed_condition,viz_title,viz_spec_json)
VALUES(@stack_asset,'SINGLE_CHOICE',
 '入栈顺序为 1,2,3,4,5，下列哪个**不可能**是出栈序列？',
 JSON_ARRAY(JSON_OBJECT('label','A','content','1,2,3,4,5'),JSON_OBJECT('label','B','content','5,4,3,2,1'),JSON_OBJECT('label','C','content','3,2,1,5,4'),JSON_OBJECT('label','D','content','4,3,5,1,2')),
 'D','D：压入 1–4 后弹出 4、3，再压入 5 并弹出 5。此时栈里剩 1、2，栈顶是 2，序列却要先取 1，做不到。A 每压一个就弹一个；B 全部压入后依次弹出；C 就是原题的正确选项。',
 3,'APPROVED','Claude 内容复核通过',0,CURRENT_TIMESTAMP,
 '问法由“哪个可能”改为“哪个不可能”，入栈顺序不变。',
 'D 卡在哪一步',CAST('{"scene":"stack","op":"check","seq":[4,3,5,1,2],"pauseAt":["stack-buried"]}' AS JSON));
