-- AI 初稿经独立 Codex 冷读；等待 Claude 页面审核，所有新内容保持 PENDING/DRAFT。
SET @bt_course=(SELECT id FROM course WHERE content_key='cs408-data-structures' AND deleted=0);
SET @bt_kp=(SELECT id FROM knowledge_point WHERE content_key='408-trees' AND deleted=0);

-- te-01
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('非空二叉树有 9 个结点，以两个孩子指针域表示每个结点。进行线索化前，一共有多少个空孩子指针域？','SINGLE_CHOICE',@bt_course,3,'每个结点有两个孩子指针域，共 `2*9=18` 个；非空二叉树的边数为 `9-1=8`，故非空孩子域有 8 个。空域为 `18-8=10` 个；一般有 `n` 个结点时，空孩子域为 `n+1` 个。','线索二叉树的空指针域',2,1,0,0,'AI_GENERATED','m5-draft:te-01','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'8 个','A',0,1),
(@bt_q,'9 个','B',0,2),
(@bt_q,'10 个','C',1,3),
(@bt_q,'18 个','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'每个结点有两个孩子指针域，共 `2*9=18` 个；非空二叉树的边数为 `9-1=8`，故非空孩子域有 8 个。空域为 `18-8=10` 个；一般有 `n` 个结点时，空孩子域为 `n+1` 个。','AI_GENERATED',NULL,NULL,'原创 · te-01',NULL,'PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把边数当空域数','8 是这棵树的边数，对应非空孩子指针域，空域还需从全部指针域中扣除这些域。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'少算根的差额','总指针域有 18 个，非空孩子域对应 8 条边，两者相减不是 9。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'没有扣除非空域','18 是全部孩子指针域，真实孩子占据的域不能算空域。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'先数所有结点一共提供多少个孩子指针域。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'每条父子边恰好占用一个非空孩子指针域。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'在根带两个叶的小树上，分别数全部孩子域、非空域和空域，比较它们的数量关系。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 树章节扩展待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','保持相同存储约定，改为有 4 个结点的非空二叉树，空孩子指针域有多少个？',CAST('[{"label":"A","content":"3 个"},{"label":"B","content":"4 个"},{"label":"C","content":"5 个"},{"label":"D","content":"8 个"}]' AS JSON),'C','总域数为 8，非空域对应 3 条边，空域为 5。',3,'PENDING','结点数从 9 改为 4。',NULL,NULL);

-- te-02
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('在不带头结点的中序线索二叉树中，约定标志为 0 表示真实孩子、1 表示线索。结点 `P` 没有右孩子，右标志为 1，且 `P` 不是中序最后一个结点。它的右指针指向谁？','SINGLE_CHOICE',@bt_course,3,'右标志为 1 时，右域不是孩子，而是记录中序后继的线索。题干排除了中序最后结点的边界，所以该域指向 `P` 在中序中的下一个结点。','中序线索的含义',2,1,0,0,'AI_GENERATED','m5-draft:te-02','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'`P` 的父结点','A',0,1),
(@bt_q,'整棵树的根','B',0,2),
(@bt_q,'`P` 的中序后继','C',1,3),
(@bt_q,'`P` 的层序后继','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'右标志为 1 时，右域不是孩子，而是记录中序后继的线索。题干排除了中序最后结点的边界，所以该域指向 `P` 在中序中的下一个结点。','AI_GENERATED',NULL,NULL,'原创 · te-02',NULL,'PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'线索与父指针混淆','中序后继有时是父结点，但也可能是更高祖先，不能把右线索统一解释为父指针。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把返回位置固定为根','线索按遍历相邻关系连接，不统一指向根。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'混淆遍历次序','本题明确按中序建立线索，层序相邻不决定右线索。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'先区分标志表示的真实孩子边与遍历线索。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'中序线索利用原来的空域，记录该次遍历中的相邻结点。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'另画三个结点的树，写出中序，再看一个空孩子域怎样让遍历继续，而不用重新从根寻找。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 树章节扩展待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','若 `P` 的右标志改为 0，且右孩子为 `R`，那么求 `P` 的中序后继应怎样做？',CAST('[{"label":"A","content":"直接取 `P` 的父结点"},{"label":"B","content":"从 `R` 出发沿真实左孩子走到最左结点"},{"label":"C","content":"直接取整棵树最左结点"},{"label":"D","content":"沿 `R` 的所有左线索一直走"}]' AS JSON),'B','有右子树时，中序在 `P` 后先访问右子树最左结点；只沿左标志为 0 的真实孩子走，不能把线索继续当孩子。',3,'PENDING','`P` 从无右孩子改为有真实右孩子。',NULL,NULL);

-- te-03
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('森林依次由两棵有序树组成：第一棵根 `A` 有从左到右两个孩子 `D`、`E`；第二棵根 `B` 只有孩子 `F`。按左孩子右兄弟规则转换为二叉树，`A` 的左右指针分别指向谁？','SINGLE_CHOICE',@bt_course,3,'左孩子指针连接原树的第一个孩子，故 `A` 左指针指向 `D`；森林的两个根按顺序为兄弟，故 `A` 右指针指向 `B`。其余非空边为 `D` 右指针到 `E`、`B` 左指针到 `F`。','森林的左孩子右兄弟表示',2,1,0,0,'AI_GENERATED','m5-draft:te-03','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'`D`、`E`','A',0,1),
(@bt_q,'`B`、`D`','B',0,2),
(@bt_q,'`E`、`B`','C',0,3),
(@bt_q,'`D`、`B`','D',1,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'左孩子指针连接原树的第一个孩子，故 `A` 左指针指向 `D`；森林的两个根按顺序为兄弟，故 `A` 右指针指向 `B`。其余非空边为 `D` 右指针到 `E`、`B` 左指针到 `F`。','AI_GENERATED','森林转换后的二叉树结构',CAST('{"scene":"binary-tree","op":"preorder","tree":["A","D","B",null,"E","F"],"pauseAt":["tree-first-visit"]}' AS JSON),'原创 · te-03',NULL,'PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把第二个孩子直接接在根右侧','右指针连接同层兄弟；根 `A` 的右指针连接森林的下一个根 `B`，`E` 应通过 `D` 的右指针表示。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'混淆左右指针职责','左指针连接第一个孩子，右指针连接下一个兄弟；不能反过来。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把最后一个孩子当首孩子','`A` 的孩子按 `D`、`E` 有序，第一个孩子为 `D`，`E` 是 `D` 的下一个兄弟。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'分别给左指针和右指针写上在这种表示中的职责。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'森林的各个根也按顺序看作兄弟。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'另取根 `X` 的孩子 `Y`、`Z`，分别标出第一个孩子边和下一兄弟边，再把另一棵根接入森林。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 树章节扩展待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','沿用转换后的二叉树，`D` 的右指针与 `B` 的左指针分别指向谁？',CAST('[{"label":"A","content":"`A`、`F`"},{"label":"B","content":"`E`、`F`"},{"label":"C","content":"`E`、`A`"},{"label":"D","content":"`B`、`E`"}]' AS JSON),'B','`D` 的下一个兄弟是 `E`，`B` 的第一个孩子是 `F`，所以分别为 `E`、`F`。',3,'PENDING','由询问根的两域改为询问孩子与下一棵根的域。','转换结果的二叉树先序（只展示转换后结构）',CAST('{"scene":"binary-tree","op":"preorder","tree":["A","D","B",null,"E","F"],"pauseAt":["tree-first-visit"]}' AS JSON));

-- te-04
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('将一棵非空有序树按左孩子右兄弟规则转换为二叉树。转换后二叉树的中序遍历，等同于原有序树的哪种遍历？原树按从左到右处理孩子。','SINGLE_CHOICE',@bt_course,3,'转换后二叉树中序先处理左孩子对应的整组孩子及其右兄弟，最后访问当前根，递归得到原树从左到右的后根次序。例如原树根 `X`、孩子 `Y`、`Z`，转换后 `X` 左 `Y`、`Y` 右 `Z`，中序 `Y`、`Z`、`X`，正是原树后根。','树与二叉树遍历的对应',2,1,0,0,'AI_GENERATED','m5-draft:te-04','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'先根遍历','A',0,1),
(@bt_q,'后根遍历','B',1,2),
(@bt_q,'层序遍历','C',0,3),
(@bt_q,'按结点编号排序','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'转换后二叉树中序先处理左孩子对应的整组孩子及其右兄弟，最后访问当前根，递归得到原树从左到右的后根次序。例如原树根 `X`、孩子 `Y`、`Z`，转换后 `X` 左 `Y`、`Y` 右 `Z`，中序 `Y`、`Z`、`X`，正是原树后根。','AI_GENERATED',NULL,NULL,'原创 · te-04',NULL,'PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'只看根与左孩子','二叉树中序先处理左子树再访问当前结点；这会使原树各孩子的后根结果先于原根输出。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把兄弟连接当队列','右兄弟边只是结构表示，不会把遍历变成逐层输出。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把结构次序当数值次序','结点编号或大小不参与遍历规则。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'先在只有一个根与两个叶孩子的原树上做转换。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'追踪二叉树的左子树包含了原树哪些孩子，以及这些孩子的兄弟。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'可用 `X` 的有序孩子 `Y`、`Z` 做小例子，分别写出转换前后要比较的两个次序，再扩展到有子树的孩子。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 树章节扩展待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','将一个有序森林按左孩子右兄弟规则转换为二叉树。森林先序（依次对每棵树做先根）对应二叉树的哪种遍历？',CAST('[{"label":"A","content":"先序"},{"label":"B","content":"中序"},{"label":"C","content":"后序"},{"label":"D","content":"层序"}]' AS JSON),'A','二叉树先序先访问当前原树根，再访问其左孩子所表示的孩子组，随后通过右兄弟边处理下一棵树，因此对应森林先序。',3,'PENDING','从一棵树的后根对应，改问森林先序对应。',NULL,NULL);

-- te-05
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('字符集含 6 个字符，出现频次分别为 3、4、5、6、8、10。为它们构造二元哈夫曼编码，加权平均码长是多少？','SINGLE_CHOICE',@bt_course,3,'依次合并得到权重 7、11、15、21、36，加权路径长度等于这些合并权重之和，即 90。总频次为 36，加权平均码长为 `90/36=2.5`。这是哈夫曼树的最小加权结果，不是各叶深度的简单平均。','哈夫曼树的加权平均码长',2,1,0,0,'AI_GENERATED','m5-draft:te-05','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'2.4','A',0,1),
(@bt_q,'2.5','B',1,2),
(@bt_q,'2.67','C',0,3),
(@bt_q,'2.75','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'依次合并得到权重 7、11、15、21、36，加权路径长度等于这些合并权重之和，即 90。总频次为 36，加权平均码长为 `90/36=2.5`。这是哈夫曼树的最小加权结果，不是各叶深度的简单平均。','AI_GENERATED',NULL,NULL,'2023 年 408 真题 第 4 题（按实际收录同义表述）；依据：计算机考研杂货铺收录（非官方原卷）','https://csgraduates.com/study_methods/408quiz/2023/','PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'漏算合并路径代价','应将每轮合并权重累加得到总加权路径长度，再除以总频次，不能直接按叶数估计。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'用平均层数替代加权平均','码长必须按字符频次加权；频次较大的字符一般使用较短码字。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'没有按最小权重合并','每轮应合并当前最小的两棵树，不能固定按原输入相邻配对。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'先区分加权路径长度与加权平均码长。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'用优先队列的思路，每次合并当前最小的两项，并把新权重放回。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'在频次为 1、2、3 的小例子中，把每次合并的权重加起来，再除以全部字符的总频次。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 树章节扩展待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','改为四个字符，频次为 1、2、4、8，仍用二元哈夫曼编码。加权平均码长是多少？',CAST('[{"label":"A","content":"1.5"},{"label":"B","content":"`5/3`"},{"label":"C","content":"2"},{"label":"D","content":"`7/3`"}]' AS JSON),'B','合并权重依次为 3、7、15，加权路径长度为 25；总频次 15，平均码长为 `25/15=5/3`。',3,'PENDING','频次集合改变，仍问加权平均码长。',NULL,NULL);

-- te-06
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('以下每组都是为四个不同字符分配的二进制码字。哪一组是前缀编码，即没有一个码字是另一个码字的前缀？','SINGLE_CHOICE',@bt_course,3,'`C` 中 0 与其余以 1 开头的码字区分；10 与 110、111 的前两位不同；110、111 等长且不同，互不为前缀。因此只有 `C` 满足定义。前缀编码可以即时译码，但满足前缀条件本身不保证对给定频次最优。','前缀编码',2,1,0,0,'AI_GENERATED','m5-draft:te-06','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'0、01、10、11','A',0,1),
(@bt_q,'10、101、110、111','B',0,2),
(@bt_q,'0、10、110、111','C',1,3),
(@bt_q,'00、01、0、11','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'`C` 中 0 与其余以 1 开头的码字区分；10 与 110、111 的前两位不同；110、111 等长且不同，互不为前缀。因此只有 `C` 满足定义。前缀编码可以即时译码，但满足前缀条件本身不保证对给定频次最优。','AI_GENERATED',NULL,NULL,'原创 · te-06',NULL,'PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'忽略短码字包含关系','码字 0 是码字 01 的前缀，读到 0 时不能立即确定是否还有后续位属于同一码字。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'只检查首位是否相同','码字 10 是 101 的前缀，即使其余码字不同，也不满足前缀条件。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'认为长短混合必可区分','码字 0 是 00 与 01 的前缀，违反定义。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'逐个取短码字，检查较长码字是否由它开头。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'也可画二进制编码树，检查某个码字终点是否还是另一码字路径上的中间结点。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'例如码字 1 与 10 不能同时作为前缀编码中的两个字符码字；检查时看完整前缀，而非只比较长度。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 树章节扩展待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','下列三字符码字集合中，哪组不是前缀编码？',CAST('[{"label":"A","content":"0、10、11"},{"label":"B","content":"00、01、1"},{"label":"C","content":"10、110、111"},{"label":"D","content":"0、10、101"}]' AS JSON),'D','`D` 中 10 是 101 的前缀，其余三组均没有某个完整码字作为另一码字前缀。',3,'PENDING','从四字符集合中选合法，改为三字符集合中选不合法。',NULL,NULL);

-- te-07
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('7 个字符的出现频次依次为 2、3、4、6、8、10、11。构造二元哈夫曼编码，码长不小于 3 的字符有几个？','SINGLE_CHOICE',@bt_course,3,'合并权重依次为 5、9、14、19、25、44。根的两子树为 19、25：19 下有权重 9 和字符 10，25 下有字符 11 和权重 14。字符 10、11 的码长为 2；字符 4、6、8 的码长为 3；字符 2、3 的码长为 4，所以共有 5 个字符码长不小于 3。','哈夫曼码长与叶深度',2,1,0,0,'AI_GENERATED','m5-draft:te-07','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'2 个','A',0,1),
(@bt_q,'3 个','B',0,2),
(@bt_q,'4 个','C',0,3),
(@bt_q,'5 个','D',1,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'合并权重依次为 5、9、14、19、25、44。根的两子树为 19、25：19 下有权重 9 和字符 10，25 下有字符 11 和权重 14。字符 10、11 的码长为 2；字符 4、6、8 的码长为 3；字符 2、3 的码长为 4，所以共有 5 个字符码长不小于 3。','AI_GENERATED',NULL,NULL,'2025 年 408 真题 第 5 题（按实际收录同义表述）；依据：计算机考研杂货铺收录（非官方原卷）','https://csgraduates.com/study_methods/408quiz/2025/','PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'只数最深层叶结点','频次 2、3 的两个叶码长为 4，但阈值还包含码长为 3 的叶。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'只统计码长恰为三的叶','码长不小于 3 同时包含码长为 3 和更长的字符。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'遗漏一个低频叶','频次 2、3、4、6、8 对应的五个叶均达到阈值，不能只按合并轮数统计叶数。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'二元码字长度对应从根到叶经过的边数。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'先完成每轮最小权重合并，再从最后一次合并的根向下数边。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'另画有三片叶的二元编码树，根的某个孩子可能直接是叶，另一侧还会继续分支，不能用合并轮数直接当每个叶的深度。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 树章节扩展待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','保持同一组频次和二元哈夫曼树，把阈值改为码长不小于 4，符合条件的字符有几个？',CAST('[{"label":"A","content":"2 个"},{"label":"B","content":"3 个"},{"label":"C","content":"4 个"},{"label":"D","content":"5 个"}]' AS JSON),'A','只有频次 2、3 的字符码长为 4，其余码长为 2 或 3，故有 2 个。',3,'PENDING','只把码长阈值从 3 改为 4。',NULL,NULL);

-- te-08
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('二叉排序树的关键字互异，根为 8，左右孩子为 4、12；4 的左右孩子为 2、6，12 的左右孩子为 10、14。中序遍历序列是？','SINGLE_CHOICE',@bt_course,3,'左子树中序为 2、4、6，随后是根 8，再是右子树 10、12、14，得到严格递增序列 2、4、6、8、10、12、14。关键字互异是严格递增的前提。','二叉排序树的中序有序性',2,1,0,0,'AI_GENERATED','m5-draft:te-08','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'`8,4,2,6,12,10,14`','A',0,1),
(@bt_q,'`2,6,4,10,14,12,8`','B',0,2),
(@bt_q,'`2,4,6,8,10,12,14`','C',1,3),
(@bt_q,'`14,12,10,8,6,4,2`','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'左子树中序为 2、4、6，随后是根 8，再是右子树 10、12、14，得到严格递增序列 2、4、6、8、10、12、14。关键字互异是严格递增的前提。','AI_GENERATED','二叉排序树的中序',CAST('{"scene":"binary-tree","op":"inorder","tree":["8","4","12","2","6","10","14"],"pauseAt":["tree-first-visit"]}' AS JSON),'原创 · te-08',NULL,'PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把根先输出','中序先访问左子树，根 8 不会最先输出；这是先序次序。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把中序写成后序','先访问左右子树最后访问根是后序，不是中序。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'交换了左右方向','题干是左子树小、右子树大的二叉排序树，中序按左、根、右得到升序，而非降序。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'先把中序的访问顺序与二叉排序树左右关键字约束结合。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'每个子树也要按同一顺序访问，不能只将根的位置改一遍。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'另取根 5、左叶 3、右叶 7，写出中序，并观察它与三个关键字大小的关系。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 树章节扩展待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','同一棵二叉排序树改为后序遍历，序列是？',CAST('[{"label":"A","content":"`2,4,6,8,10,12,14`"},{"label":"B","content":"`8,4,2,6,12,10,14`"},{"label":"C","content":"`2,6,4,12,10,14,8`"},{"label":"D","content":"`2,6,4,10,14,12,8`"}]' AS JSON),'D','左子树后序为 2、6、4，右子树为 10、14、12，最后访问根 8。',3,'PENDING','保持树形和关键字不变，改问后序。','同一二叉排序树的后序',CAST('{"scene":"binary-tree","op":"postorder","tree":["8","4","12","2","6","10","14"],"pauseAt":["tree-first-visit"]}' AS JSON));

-- te-09
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('在关键字互异的二叉排序树中查找 60。以下从根到目标的结点访问序列中，哪一条不可能出现？','SINGLE_CHOICE',@bt_course,3,'目标 60 大于根 50，下一步必须进入 50 的右子树，其中所有关键字都大于 50。`B` 的第二个结点却为 30，因此不可能。其余路径可分别按比较方向构造相应的二叉排序树。','二叉排序树查找路径',2,1,0,0,'AI_GENERATED','m5-draft:te-09','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'`50,80,70,60`','A',0,1),
(@bt_q,'`50,30,70,60`','B',1,2),
(@bt_q,'`90,20,70,60`','C',0,3),
(@bt_q,'`40,90,50,60`','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'目标 60 大于根 50，下一步必须进入 50 的右子树，其中所有关键字都大于 50。`B` 的第二个结点却为 30，因此不可能。其余路径可分别按比较方向构造相应的二叉排序树。','AI_GENERATED',NULL,NULL,'原创 · te-09',NULL,'PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把局部下降误当越界','60 大于 50 进入右子树，再小于 80、70 依次向左，整个路径满足祖先给出的范围。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'忽略区间可以同时含上下界','90 后向左到 20，再向右到 70，再向左到 60，各关键字都满足相应祖先区间。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'认为路径必须单调','40 后右到 90，再左到 50，再右到 60 可以成立；访问序列本身无需数值单调。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'每次比较都由待查值与当前关键字共同决定走向。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'保留所有祖先形成的上下界，而不是只看相邻两个结点的大小。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'例如查找 25，若根 40 后进入左子树，则之后访问的结点都必须小于 40；再逐步收紧这个区间。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 树章节扩展待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','把目标改为 35，以下哪条访问序列不可能？',CAST('[{"label":"A","content":"`60,20,40,35`"},{"label":"B","content":"`20,60,30,35`"},{"label":"C","content":"`40,20,30,35`"},{"label":"D","content":"`40,60,20,35`"}]' AS JSON),'D','35 小于根 40，必须进入左子树，第二结点不可能为 60；其余三条可满足全部祖先区间。',3,'PENDING','待查关键字和候选访问路径都改变。',NULL,NULL);

-- te-10
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('向空 `AVL` 树依次插入互异关键字 30、20、10。用保持二叉排序树次序的最小局部调整恢复平衡后，根关键字是多少？约定平衡因子为左子树高减右子树高。','SINGLE_CHOICE',@bt_course,3,'插入后为30左接20、20左接10，属于30处`LL`失衡。对30做一次右旋，得到根20、左孩子10、右孩子30，保持中序10、20、30并恢复平衡。','平衡二叉树的基本旋转',2,1,0,0,'AI_GENERATED','m5-draft:te-10','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'10','A',0,1),
(@bt_q,'20','B',1,2),
(@bt_q,'30','C',0,3),
(@bt_q,'无需调整','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'插入后为30左接20、20左接10，属于30处`LL`失衡。对30做一次右旋，得到根20、左孩子10、右孩子30，保持中序10、20、30并恢复平衡。','AI_GENERATED',NULL,NULL,'原创 · te-10',NULL,'PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把新叶直接升到根','`LL`失衡对30右旋后，原左孩子20成为根，新插入的10仍为左孩子。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'保留失衡根','30的左高比右高多2，不能保持根30且完全不调整连接。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'只检查排序不检查高度','原结构虽然满足二叉排序树次序，但根30已违反`AVL`的高度差要求。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'插入后先画出普通二叉排序树的结构。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'从插入点向上寻找第一个高度差绝对值超过1的结点，并判断插入发生在哪一侧的哪一侧。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'另看根9、左孩子6、6的左孩子3，思考让中间关键字居中能否同时保持排序和减小高度差。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 树章节扩展待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','改为依次插入30、10、20，第一次需要的恢复平衡操作是？',CAST('[{"label":"A","content":"先对10左旋，再对30右旋"},{"label":"B","content":"只对30右旋"},{"label":"C","content":"只对10左旋"},{"label":"D","content":"无需调整"}]' AS JSON),'A','新结点20在30的左孩子10的右侧。先左旋10把20提升到左子树根，再右旋30，最终根20、左右孩子10和30。',3,'PENDING','插入次序改变为`LR`失衡。',NULL,NULL);

