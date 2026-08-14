-- 2026 年 408 真题数据结构解答题第 41、42 题及人工批阅评分点。
-- 来源：https://csgraduates.com/study_methods/408quiz/2026/
-- 不修改已发布且可能被历史考试引用的 22 分客观题卷，另建包含 11 道客观题和 2 道解答题的完整数据结构分卷。

SET @course_id = (
  SELECT id FROM course WHERE content_key = 'cs408-data-structures' AND deleted = 0 LIMIT 1
);
SET @kp_trees = (SELECT id FROM knowledge_point WHERE content_key = '408-trees' LIMIT 1);
SET @kp_stacks = (SELECT id FROM knowledge_point WHERE content_key = '408-stacks-queues-arrays' LIMIT 1);
SET @objective_paper_id = (
  SELECT id FROM exam_paper
  WHERE title = '2026 年 408 真题·数据结构选择题' AND exam_year = 2026 AND deleted = 0
  ORDER BY id LIMIT 1
);

INSERT INTO question (
  content, question_type, course_id, difficulty, analysis, tags, score, status, create_by,
  source_type, source_reference, deleted
) VALUES (
  '（本题满分 13 分）\n\n假定二叉搜索树使用二叉链表存储，存储结构如下：\n\n```c\ntypedef struct BSTNode {\n    int data;\n    struct BSTNode *left, *right;\n} BSTNode;\ntypedef BSTNode BTNode;\n```\n\n给一棵二叉搜索树 T 和整数 K，查找树中关键字与 K 之差的绝对值最小的所有结点，并输出该绝对值与结点中的关键字。\n\n（1）给出算法的基本思想。（4 分）\n\n（2）使用 C/C++ 描述算法思想。（8 分）',
  'SHORT_ANSWER', @course_id, 5,
  '参考思路：利用二叉搜索树中序序列递增的性质，中序遍历并维护当前最小绝对差与并列关键字。遇到更小差值时重置结果，等于最小差值时追加结果；已经越过 K 且差值开始增大后可以停止。实现需覆盖空树、命中 K、两个并列最近结点以及结果输出。',
  '408,2026真题,数据结构,二叉搜索树,算法设计', 13, 1, 1,
  'MANUAL', 'https://csgraduates.com/study_methods/408quiz/2026/#41', 0
);
SET @q41 = LAST_INSERT_ID();
INSERT INTO question_knowledge_point (question_id, knowledge_point_id) VALUES (@q41, @kp_trees);

INSERT INTO subjective_grading_point
  (question_id, point_key, title, description, reference_answer, max_score, sort_order)
VALUES
  (@q41, 'idea', '算法基本思想', '说明利用二叉搜索树有序性，维护最小绝对差并保留全部并列结点。',
   '中序遍历得到递增序列；逐结点计算 abs(K-data)，更小时重置、相等时追加，越过 K 且差值增大后可停止。', 4, 1),
  (@q41, 'implementation', 'C/C++ 算法实现', '实现遍历、差值更新、并列结果保存与安全终止，逻辑可执行。',
   '代码应正确处理空指针，维护 minDiff 和至多两个并列关键字，并保证遍历或剪枝不会漏解。', 8, 2),
  (@q41, 'output', '结果输出与边界', '输出最小差值及全部最近关键字，并覆盖命中或并列等边界。',
   '输出 minDiff 和已收集关键字；K 命中时差值为 0，并列最近结点不得漏报。', 1, 3);

INSERT INTO question (
  content, question_type, course_id, difficulty, analysis, tags, score, status, create_by,
  source_type, source_reference, deleted
) VALUES (
  '（本题满分 10 分）\n\n栈的基本操作有出栈和入栈。将序列 1,2,3,…,n 依次入栈，回答下列问题：\n\n（1）当 n=9 时，可以得到出栈序列 {2,3,1,6,4,7,5,8} 吗？可以得到出栈序列 {2,3,1,4,6,5,7,8} 吗？（2 分）\n\n（2）假设 1,2,…,n 组成任意序列的出栈序列 P1,P2,…,Pn，在序列中有 Pi、Pj、Pk（i<j<k），若该出栈序列不能由栈得到，则 Pi、Pj、Pk 的大小关系是？（2 分）\n\n（3）若 n=4，则以 2 开头的序列个数有多少个？（2 分）\n\n（4）若 n=k−1 时，出栈序列总数为 M；当 n=k 时，以 1 开头、以 2 开头及全部出栈序列的个数分别是多少？（4 分）',
  'SHORT_ANSWER', @course_id, 5,
  '参考答案：（1）第一个序列不能得到，第二个可以得到；（2）存在 i<j<k 使 Pj<Pk<Pi，即 312 模式；（3）共有 5 个；（4）以 1 开头和以 2 开头均为 M，总数为 2(2k-1)M/(k+1)。',
  '408,2026真题,数据结构,栈,出栈序列,卡特兰数', 10, 1, 1,
  'MANUAL', 'https://csgraduates.com/study_methods/408quiz/2026/#42', 0
);
SET @q42 = LAST_INSERT_ID();
INSERT INTO question_knowledge_point (question_id, knowledge_point_id) VALUES (@q42, @kp_stacks);

INSERT INTO subjective_grading_point
  (question_id, point_key, title, description, reference_answer, max_score, sort_order)
VALUES
  (@q42, 'sequence_check', '两组出栈序列判断', '分别判断两组序列能否由栈得到，并给出一致的理由。',
   '第一组不能得到，第二组可以得到；可用模拟入出栈或 312 禁止模式说明。', 2, 1),
  (@q42, 'forbidden_pattern', '非法序列大小关系', '写出不能由栈得到时存在的三元素关系。',
   '存在 i<j<k，使 Pj<Pk<Pi。', 2, 2),
  (@q42, 'n4_count', 'n=4 的计数', '正确给出以 2 开头的合法出栈序列个数。',
   '共 5 个。', 2, 3),
  (@q42, 'catalan_recurrence', '递推计数', '分别给出以 1、2 开头的数量以及总数。',
   '以 1 开头为 M，以 2 开头为 M；总数 Ck=2(2k-1)M/(k+1)。', 4, 4);

INSERT INTO exam_paper (
  title, description, course_id, total_score, duration, question_count, status, create_by,
  paper_type, exam_name, exam_year, source_reference, source_verified, deleted
) VALUES (
  '2026 年 408 真题·数据结构部分',
  '数据结构第 1–11 题客观题与第 41、42 题综合应用题。客观题由服务端自动判分；综合应用题提交后按评分点人工批阅。',
  @course_id, 45, 60, 13, 0, 1,
  'OFFICIAL_EXAM', '全国硕士研究生招生考试计算机学科专业基础综合', 2026,
  'https://csgraduates.com/study_methods/408quiz/2026/', 1, 0
);
SET @full_paper_id = LAST_INSERT_ID();

INSERT INTO exam_question (
  exam_paper_id, question_id, sort_order, score, section_title,
  major_question_number, minor_question_number, subquestion_number, display_number
)
SELECT @full_paper_id, question_id, sort_order, score, section_title,
       major_question_number, minor_question_number, subquestion_number, display_number
FROM exam_question
WHERE exam_paper_id = @objective_paper_id
ORDER BY sort_order;

INSERT INTO exam_question (
  exam_paper_id, question_id, sort_order, score, section_title,
  major_question_number, minor_question_number, subquestion_number, display_number
) VALUES
  (@full_paper_id, @q41, 12, 13, '二、综合应用题（数据结构）', '二', '41', NULL, '第41题'),
  (@full_paper_id, @q42, 13, 10, '二、综合应用题（数据结构）', '二', '42', NULL, '第42题');

UPDATE exam_paper SET status = 1 WHERE id = @full_paper_id;
