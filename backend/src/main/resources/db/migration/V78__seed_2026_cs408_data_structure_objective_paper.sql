-- 2026 年全国硕士研究生招生考试 408 真题的数据结构客观题分区。
-- 用户提供的离线文件是 2025 年页面；本迁移改用页面侧栏指向并已核对标题、正文和题号的 2026 页面：
-- https://csgraduates.com/study_methods/408quiz/2026/
--
-- 当前课程仅覆盖 408 数据结构，因此不把组成原理、操作系统和计算机网络题错误归入本课程。
-- 第 41、42 题为综合应用题，现有 SHORT_ANSWER 关键词判分不足以形成可信考试成绩，暂不进入自动判分试卷。

SET @course_id = (
  SELECT id FROM course WHERE content_key = 'cs408-data-structures' AND deleted = 0 LIMIT 1
);
SET @kp_linear_lists = (SELECT id FROM knowledge_point WHERE content_key = '408-linear-lists' LIMIT 1);
SET @kp_trees = (SELECT id FROM knowledge_point WHERE content_key = '408-trees' LIMIT 1);
SET @kp_graphs = (SELECT id FROM knowledge_point WHERE content_key = '408-graphs' LIMIT 1);
SET @kp_sorting = (SELECT id FROM knowledge_point WHERE content_key = '408-sorting' LIMIT 1);

INSERT INTO question (
  content, question_type, course_id, difficulty, analysis, tags, score, status, create_by, deleted
) VALUES (
  '当存储空间有足够的空闲空间时，在保持表内元素顺序相对不变的情况下，下列哪些操作会必然导致产生移动次数（ ）。\n\nI. 表头插入一个元素  \nII. 表头删除一个元素  \nIII. 表尾插入一个元素  \nIV. 表尾删除一个元素',
  'SINGLE_CHOICE', @course_id, 3,
  '顺序存储要求元素连续。表头插入需要把原有元素整体后移，表头删除需要把剩余元素整体前移；在存储空间足够时，表尾插入和删除不需要移动其他元素，因此选 I、II。',
  '408,2026真题,数据结构,线性表', 2, 1, 1, 0
);
SET @q1 = LAST_INSERT_ID();
INSERT INTO question_option (question_id, content, option_label, is_correct, sort_order) VALUES
  (@q1, 'I、II', 'A', 1, 1),
  (@q1, 'I、III', 'B', 0, 2),
  (@q1, 'II、IV', 'C', 0, 3),
  (@q1, 'III、IV', 'D', 0, 4);
INSERT INTO question_knowledge_point (question_id, knowledge_point_id) VALUES (@q1, @kp_linear_lists);

INSERT INTO question (
  content, question_type, course_id, difficulty, analysis, tags, score, status, create_by, deleted
) VALUES (
  '设有一个双向链表 `L`，结构为 `[p2, p1]`，头结点为 `head`。初始时 `head = cu`。现要将每个结点的 `p2` 指向 `p1` 指向结点的直接后继，应该进行的操作是（ ）。',
  'SINGLE_CHOICE', @course_id, 3,
  '非尾结点应执行 `cu->p2 = cu->p1->p1`，尾结点的 `p1` 为空，必须把 `p2` 置空以避免非法访问；无论是否为尾结点，每轮都要推进 `cu = cu->p1`，否则会死循环。',
  '408,2026真题,数据结构,双向链表', 2, 1, 1, 0
);
SET @q2 = LAST_INSERT_ID();
INSERT INTO question_option (question_id, content, option_label, is_correct, sort_order) VALUES
  (@q2, 'while(cu!=NULL) {cu->p2=cu->p1->p1; cu=cu->p1;}', 'A', 0, 1),
  (@q2, 'while(cu!=NULL && cu->p2!=NULL) {cu->p2=cu->p1->p1; cu=cu->p1;}', 'B', 0, 2),
  (@q2, 'while(cu!=NULL) {if(cu->p1!=NULL) {cu->p2=cu->p1->p1; cu=cu->p1;}}', 'C', 0, 3),
  (@q2, 'while(cu!=NULL) {if(cu->p1!=NULL) {cu->p2=cu->p1->p1;} else {cu->p2=NULL;} cu=cu->p1;}', 'D', 1, 4);
INSERT INTO question_knowledge_point (question_id, knowledge_point_id) VALUES (@q2, @kp_linear_lists);

INSERT INTO question (
  content, question_type, course_id, difficulty, analysis, tags, score, status, create_by, deleted
) VALUES (
  '已知二叉树 T 的中序遍历为 b, e, d, f, c, a, g，层序遍历为 a, b, g, c, d, e, f，则其后序遍历序列为（ ）。',
  'SINGLE_CHOICE', @course_id, 3,
  '层序首元素 a 是根，中序序列据此分出左子树 b,e,d,f,c 和右子树 g。继续用层序确定各子树根，可得左子树后序为 e,f,d,c,b，右子树后序为 g，最后访问根 a。',
  '408,2026真题,数据结构,二叉树遍历', 2, 1, 1, 0
);
SET @q3 = LAST_INSERT_ID();
INSERT INTO question_option (question_id, content, option_label, is_correct, sort_order) VALUES
  (@q3, 'c, e, d, f, b, g, a', 'A', 0, 1),
  (@q3, 'c, e, f, d, b, g, a', 'B', 0, 2),
  (@q3, 'e, f, d, c, b, g, a', 'C', 1, 3),
  (@q3, 'e, g, f, d, b, c, a', 'D', 0, 4);
INSERT INTO question_knowledge_point (question_id, knowledge_point_id) VALUES (@q3, @kp_trees);

INSERT INTO question (
  content, question_type, course_id, difficulty, analysis, tags, score, status, create_by, deleted
) VALUES (
  '森林 F 中有 5 棵树，其结点个数分别为 2、3、4、5、7，森林中树的次序可以任意，问 F 对应的二叉树最小高度为（ ）。',
  'SINGLE_CHOICE', @course_id, 4,
  '森林采用左孩子右兄弟表示转换为二叉树。为了压低由各树根形成的右链带来的高度，应把对应二叉树高度较大的树放在前面。按最优次序计算各树高度与其右链偏移量，最大值为 6。',
  '408,2026真题,数据结构,树与森林', 2, 1, 1, 0
);
SET @q4 = LAST_INSERT_ID();
INSERT INTO question_option (question_id, content, option_label, is_correct, sort_order) VALUES
  (@q4, '5', 'A', 0, 1),
  (@q4, '6', 'B', 1, 2),
  (@q4, '8', 'C', 0, 3),
  (@q4, '10', 'D', 0, 4);
INSERT INTO question_knowledge_point (question_id, knowledge_point_id) VALUES (@q4, @kp_trees);

INSERT INTO question (
  content, question_type, course_id, difficulty, analysis, tags, score, status, create_by, deleted
) VALUES (
  '假设二叉树中结点权值为 a=1，b=2，c=4，d=5，e=8，f=10，g=12。当带权路径长度（WPL）最小时，与结点 e（权值 8）处于相同深度的结点是（ ）。',
  'SINGLE_CHOICE', @course_id, 3,
  '按哈夫曼算法依次合并最小权值：1 与 2 得 3，3 与 4 得 7，5 与 7 得 12，8 与 10 得 18，两个 12 得 24，最后合并 18 与 24。由所得树可知 e、f、g 处于同一深度。',
  '408,2026真题,数据结构,哈夫曼树', 2, 1, 1, 0
);
SET @q5 = LAST_INSERT_ID();
INSERT INTO question_option (question_id, content, option_label, is_correct, sort_order) VALUES
  (@q5, 'd', 'A', 0, 1),
  (@q5, 'g', 'B', 0, 2),
  (@q5, 'd、f', 'C', 0, 3),
  (@q5, 'f、g', 'D', 1, 4);
INSERT INTO question_knowledge_point (question_id, knowledge_point_id) VALUES (@q5, @kp_trees);

INSERT INTO question (
  content, question_type, course_id, difficulty, analysis, tags, score, status, create_by, deleted
) VALUES (
  '有向图 G=(V,E) 采用邻接表存储，求某顶点入度的时间复杂度为（ ）。',
  'SINGLE_CHOICE', @course_id, 3,
  '求某顶点入度需要扫描所有顶点的出边链表，即访问 |V| 个表头和总计 |E| 条边，时间复杂度为 O(|V|+|E|)，与 O(max(|V|,|E|)) 同阶。',
  '408,2026真题,数据结构,图,邻接表', 2, 1, 1, 0
);
SET @q6 = LAST_INSERT_ID();
INSERT INTO question_option (question_id, content, option_label, is_correct, sort_order) VALUES
  (@q6, 'O(|V|)', 'A', 0, 1),
  (@q6, 'O(min(|V|,|E|))', 'B', 0, 2),
  (@q6, 'O(|E|)', 'C', 0, 3),
  (@q6, 'O(max(|V|,|E|))', 'D', 1, 4);
INSERT INTO question_knowledge_point (question_id, knowledge_point_id) VALUES (@q6, @kp_graphs);

INSERT INTO question (
  content, question_type, course_id, difficulty, analysis, tags, score, status, create_by, deleted
) VALUES (
  '设有向图 G=(V,E)，其中顶点集 V 的大小为 n=|V|，每条边 e∈E 都标记有一个字符（不同边可标记相同字符）。定义字符串集 S 为所有由 G 中任意一条路径（路径可包含单个顶点，对应空字符串）上的边标记按顺序拼接而成的字符串的集合。以下说法错误的是（ ）。',
  'SINGLE_CHOICE', @course_id, 4,
  '无环图的路径不会重复顶点，最长路径至多有 n-1 条边，所以 S 有限且不存在长度为 n 的字符串。有环图可以沿环产生任意长字符串，同时 S 也包含长度为 0 的空字符串。',
  '408,2026真题,数据结构,图,路径', 2, 1, 1, 0
);
SET @q7 = LAST_INSERT_ID();
INSERT INTO question_option (question_id, content, option_label, is_correct, sort_order) VALUES
  (@q7, '若 G 无环，则 S 是有限集', 'A', 0, 1),
  (@q7, '若 G 无环，则 S 中存在长度为 n 的字符串', 'B', 1, 2),
  (@q7, '若 G 有环，则 S 中存在长度大于 n 的字符串', 'C', 0, 3),
  (@q7, '若 G 有环，则 S 中存在长度小于 2n 的字符串', 'D', 0, 4);
INSERT INTO question_knowledge_point (question_id, knowledge_point_id) VALUES (@q7, @kp_graphs);

INSERT INTO question (
  content, question_type, course_id, difficulty, analysis, tags, score, status, create_by, deleted
) VALUES (
  '已知平衡二叉树（AVL 树）的定义为：树中任意一个结点的左右子树高度差的绝对值不超过 1，且左右子树均为平衡二叉树。若某平衡二叉树的高度为 4（根结点的高度记为 1），则其根结点的左右子树结点数之差最多为（ ）。',
  'SINGLE_CHOICE', @course_id, 3,
  '要使结点数之差最大，可令一侧为高度 3 的满二叉树，共 7 个结点；另一侧高度为 2 且取最少结点数，共 2 个结点。两侧高度差仍为 1，因此最大结点数差为 5。',
  '408,2026真题,数据结构,AVL树', 2, 1, 1, 0
);
SET @q8 = LAST_INSERT_ID();
INSERT INTO question_option (question_id, content, option_label, is_correct, sort_order) VALUES
  (@q8, '1', 'A', 0, 1),
  (@q8, '2', 'B', 0, 2),
  (@q8, '3', 'C', 0, 3),
  (@q8, '5', 'D', 1, 4);
INSERT INTO question_knowledge_point (question_id, knowledge_point_id) VALUES (@q8, @kp_trees);

INSERT INTO question (
  content, question_type, course_id, difficulty, analysis, tags, score, status, create_by, deleted
) VALUES (
  '使用直接插入排序对序列进行升序排序，以下比较次数最少的是（ ）。',
  'SINGLE_CHOICE', @course_id, 3,
  '逐个插入并从已排序区间末端向前比较，四个序列的比较次数分别为 9、8、16、16，因此第二个序列比较次数最少。',
  '408,2026真题,数据结构,直接插入排序', 2, 1, 1, 0
);
SET @q9 = LAST_INSERT_ID();
INSERT INTO question_option (question_id, content, option_label, is_correct, sort_order) VALUES
  (@q9, '30,27,56,41,80,95,69', 'A', 0, 1),
  (@q9, '31,43,26,55,63,99,77', 'B', 1, 2),
  (@q9, '61,84,51,23,34,91,40', 'C', 0, 3),
  (@q9, '93,32,48,81,50,21,72', 'D', 0, 4);
INSERT INTO question_knowledge_point (question_id, knowledge_point_id) VALUES (@q9, @kp_sorting);

INSERT INTO question (
  content, question_type, course_id, difficulty, analysis, tags, score, status, create_by, deleted
) VALUES (
  '现有 n 名学生的成绩记录，每位学生的记录包含两门课程的成绩：课程 1（记为 C1）和课程 2（记为 C2）。排序规则如下：\n\n1. 首先依据 C1 成绩升序排列；\n2. 若两名学生的 C1 成绩相同，则依据其总分（C1+C2）升序排列。\n\n下列排序算法中，最适合实现上述需求的是（ ）。',
  'SINGLE_CHOICE', @course_id, 3,
  '这是整数多关键字排序。可先按低优先级的总分稳定排序，再按高优先级的 C1 稳定排序；基数排序适合这种按关键字逐级稳定排序的场景，其余三个选项都不是稳定排序。',
  '408,2026真题,数据结构,基数排序', 2, 1, 1, 0
);
SET @q10 = LAST_INSERT_ID();
INSERT INTO question_option (question_id, content, option_label, is_correct, sort_order) VALUES
  (@q10, '基数排序', 'A', 1, 1),
  (@q10, '快速排序', 'B', 0, 2),
  (@q10, '希尔排序', 'C', 0, 3),
  (@q10, '选择排序', 'D', 0, 4);
INSERT INTO question_knowledge_point (question_id, knowledge_point_id) VALUES (@q10, @kp_sorting);

INSERT INTO question (
  content, question_type, course_id, difficulty, analysis, tags, score, status, create_by, deleted
) VALUES (
  '在外部排序的 k 路归并过程中，归并趟数为 d。下列关于 k、d、初始归并段及内存大小的说法中，正确的是（ ）。\n\nI. k 越大，d 越小  \nII. 初始归并段数不影响 d  \nIII. 内存大小限制初始归并段的最大长度',
  'SINGLE_CHOICE', @course_id, 3,
  '若初始归并段数为 m，则归并趟数 d=ceil(log_k m)。增大 k 会减少归并趟数，初始归并段数会影响 d；生成初始归并段需要在内存中进行内部排序，所以其最大长度受内存大小限制。',
  '408,2026真题,数据结构,外部排序', 2, 1, 1, 0
);
SET @q11 = LAST_INSERT_ID();
INSERT INTO question_option (question_id, content, option_label, is_correct, sort_order) VALUES
  (@q11, 'I', 'A', 0, 1),
  (@q11, 'I、II', 'B', 0, 2),
  (@q11, 'I、III', 'C', 1, 3),
  (@q11, 'II、III', 'D', 0, 4);
INSERT INTO question_knowledge_point (question_id, knowledge_point_id) VALUES (@q11, @kp_sorting);

INSERT INTO exam_paper (
  title, description, course_id, total_score, duration, question_count, status, create_by,
  paper_type, exam_name, exam_year, source_reference, source_verified, deleted
) VALUES (
  '2026 年 408 真题·数据结构选择题',
  '2026 年全国硕士研究生招生考试计算机学科专业基础综合中数据结构客观题第 1–11 题。当前课程未纳入其他三科；数据结构综合应用题等待可信主观题判分流程后再接入。',
  @course_id, 22, 30, 11, 0, 1,
  'OFFICIAL_EXAM', '全国硕士研究生招生考试计算机学科专业基础综合', 2026,
  'https://csgraduates.com/study_methods/408quiz/2026/', 1, 0
);
SET @paper_id = LAST_INSERT_ID();

INSERT INTO exam_question (
  exam_paper_id, question_id, sort_order, score, section_title,
  major_question_number, minor_question_number, subquestion_number, display_number
) VALUES
  (@paper_id, @q1, 1, 2, '一、单项选择题（数据结构）', '一', '1', NULL, '第1题'),
  (@paper_id, @q2, 2, 2, '一、单项选择题（数据结构）', '一', '2', NULL, '第2题'),
  (@paper_id, @q3, 3, 2, '一、单项选择题（数据结构）', '一', '3', NULL, '第3题'),
  (@paper_id, @q4, 4, 2, '一、单项选择题（数据结构）', '一', '4', NULL, '第4题'),
  (@paper_id, @q5, 5, 2, '一、单项选择题（数据结构）', '一', '5', NULL, '第5题'),
  (@paper_id, @q6, 6, 2, '一、单项选择题（数据结构）', '一', '6', NULL, '第6题'),
  (@paper_id, @q7, 7, 2, '一、单项选择题（数据结构）', '一', '7', NULL, '第7题'),
  (@paper_id, @q8, 8, 2, '一、单项选择题（数据结构）', '一', '8', NULL, '第8题'),
  (@paper_id, @q9, 9, 2, '一、单项选择题（数据结构）', '一', '9', NULL, '第9题'),
  (@paper_id, @q10, 10, 2, '一、单项选择题（数据结构）', '一', '10', NULL, '第10题'),
  (@paper_id, @q11, 11, 2, '一、单项选择题（数据结构）', '一', '11', NULL, '第11题');

UPDATE exam_paper SET status = 1 WHERE id = @paper_id;
