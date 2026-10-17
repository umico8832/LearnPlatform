-- AI 初稿经独立 Codex 冷读；等待 Claude 页面审核，所有新内容保持 PENDING/DRAFT。
SET @bt_course=(SELECT id FROM course WHERE content_key='cs408-data-structures' AND deleted=0);
SET @bt_kp=(SELECT id FROM knowledge_point WHERE content_key='408-trees' AND deleted=0);

-- bt-01
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('二叉树 `T` 的中序访问中，`p`、`v`、`q` 连续出现，顺序为 `p`、`v`、`q`。已知 `v` 同时有左孩子和右孩子，以下哪项成立？','SINGLE_CHOICE',@bt_course,3,'`v` 有两个孩子，故中序相邻的 `p`、`q` 分别位于其左、右子树。`p` 是左子树的最右结点，没有右孩子；`q` 是右子树的最左结点，没有左孩子。两者可以还有另一侧的孩子，因此只得到选项 `A` 所述限制。','中序前驱与后继',2,1,0,0,'AI_GENERATED','m5-draft:bt-01','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'`p` 没有右孩子，`q` 没有左孩子','A',1,1),
(@bt_q,'`p` 没有右孩子，`q` 有左孩子','B',0,2),
(@bt_q,'`p` 有右孩子，`q` 没有左孩子','C',0,3),
(@bt_q,'`p` 有右孩子，`q` 有左孩子','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'`v` 有两个孩子，故中序相邻的 `p`、`q` 分别位于其左、右子树。`p` 是左子树的最右结点，没有右孩子；`q` 是右子树的最左结点，没有左孩子。两者可以还有另一侧的孩子，因此只得到选项 `A` 所述限制。','AI_GENERATED',NULL,NULL,'2024 年 408 真题 第 3 题（同义表述）；依据：计算机考研杂货铺收录（非官方原卷）','https://csgraduates.com/study_methods/408quiz/2024/','PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'忽略后继之前的结点','`q` 是 `v` 右子树中最先被中序访问的结点。如果 `q` 有左孩子，该左子树会在 `v` 与 `q` 之间输出，与连续出现矛盾。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'忽略前驱之后的结点','`p` 是 `v` 左子树中最后被中序访问的结点。如果 `p` 有右孩子，该右子树会在 `p` 与 `v` 之间输出，与连续出现矛盾。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把相邻访问当相邻层次','中序中的相邻关系约束的是子树访问边界。`p` 的右子树或 `q` 的左子树只要非空，就会在给定相邻结点之间插入访问。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'先把中序的左、根、右顺序应用到以 `v` 为根的子树。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'分别寻找左子树最后访问的位置和右子树最先访问的位置。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'另画一棵树，沿左子树向右走到底、沿右子树向左走到底，再检查边界处还能否继续延伸。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 二叉树待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','保持 `p`、`v`、`q` 中序连续且 `v` 有两个孩子。以下哪项仍可能成立？',CAST('[{"label":"A","content":"`p` 有右孩子"},{"label":"B","content":"`q` 有左孩子"},{"label":"C","content":"`p` 有左孩子，`q` 有右孩子"},{"label":"D","content":"`p`、`q` 都是 `v` 的祖先"}]' AS JSON),'C','可令 `v` 左孩子为 `p`、右孩子为 `q`，再给 `p` 添加左孩子 `x`、给 `q` 添加右孩子 `y`；中序为 `x`、`p`、`v`、`q`、`y`。`p`、`v`、`q` 仍相邻，故 `C` 可能成立。',3,'PENDING','由必然的缺孩子方向，改问另一侧孩子是否可能存在。',NULL,NULL);

-- bt-02
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('二叉树的结点值为正整数，数组 `R` 采用根下标为 0、左右孩子下标分别为 `2i+1`、`2i+2` 的顺序存储，-1 表示空位置。哪组数据不能表示一棵二叉树？','SINGLE_CHOICE',@bt_course,3,'非根实结点必须有实父结点，父下标为 `floor((i-1)/2)`。`D` 中下标 8 的值为 19，但父下标 3 为 -1，因此非法；下标 9 的父下标 4 为 18，并不构成问题。其余三组的所有非根实结点都有父结点。','二叉树顺序存储',2,1,0,0,'AI_GENERATED','m5-draft:bt-02','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'`R={20,15,40,-1,-1,35}`','A',0,1),
(@bt_q,'`R={15,40,10,18,35,-1,-1}`','B',0,2),
(@bt_q,'`R={15,40,10,-1,-1,-1,12}`','C',0,3),
(@bt_q,'`R={17,20,35,-1,18,45,-1,-1,19,27}`','D',1,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'非根实结点必须有实父结点，父下标为 `floor((i-1)/2)`。`D` 中下标 8 的值为 19，但父下标 3 为 -1，因此非法；下标 9 的父下标 4 为 18，并不构成问题。其余三组的所有非根实结点都有父结点。','AI_GENERATED',NULL,NULL,'2025 年 408 真题 第 3 题（补明 0 基下标）；依据：计算机考研杂货铺收录（非官方原卷）','https://csgraduates.com/study_methods/408quiz/2025/','PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把空左孩子当非法','下标 1、2 的父结点都在下标 0；下标 5 的父结点在下标 2。所有非根实结点均有存在的父结点，空位置本身合法。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把未占满数组当非法','下标 3、4 是下标 1 的孩子，父结点存在；下标 5、6 可以为空。顺序存储允许空孩子。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'误把右孩子要求成双出现','下标 6 的父结点为下标 2，值为 10，确实存在。二叉树允许只有右孩子。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'检查每个非根实结点的父结点，而不是检查数值是否递增。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'由孩子的下标反推父下标，再看父位置是否为空。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'例如根下标为 0 时，下标 4 与下标 3 共用父位置；先算出父下标，再检查这个位置。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 二叉树待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','沿用相同顺序存储约定，以下哪组数组能表示一棵非空二叉树？',CAST('[{"label":"A","content":"`R={1,-1,2,-1,-1,3}`"},{"label":"B","content":"`R={1,-1,2,3}`"},{"label":"C","content":"`R={-1,2}`"},{"label":"D","content":"`R={1,-1,2,-1,4}`"}]' AS JSON),'A','`A` 的下标 2 是根的右孩子，下标 5 是下标 2 的左孩子，父结点均存在。`B` 的下标 3、`D` 的下标 4 均依赖空的下标 1；`C` 的根为空却出现孩子。',3,'PENDING','更换数组并将不能表示改为能表示。',NULL,NULL);

-- bt-03
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('二叉树的根为 `A`，左右孩子为 `B`、`C`；`B` 只有右孩子 `D`，`C` 的左右孩子为 `E`、`F`，`D`、`E`、`F` 均为叶。其先序遍历是？','SINGLE_CHOICE',@bt_course,3,'先序按根、左、右递归访问。先输出 `A`，再完成 `B` 子树得到 `B`、`D`，再完成 `C` 子树得到 `C`、`E`、`F`，因此是 `A`、`B`、`D`、`C`、`E`、`F`。','先序遍历',2,1,0,0,'AI_GENERATED','m5-draft:bt-03','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'`A,B,D,C,E,F`','A',1,1),
(@bt_q,'`A,B,C,D,E,F`','B',0,2),
(@bt_q,'`A,D,B,C,E,F`','C',0,3),
(@bt_q,'`B,D,A,C,E,F`','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'先序按根、左、右递归访问。先输出 `A`，再完成 `B` 子树得到 `B`、`D`，再完成 `C` 子树得到 `C`、`E`、`F`，因此是 `A`、`B`、`D`、`C`、`E`、`F`。','AI_GENERATED','先序先完成 `B` 子树再进入 `C`',CAST('{"scene":"binary-tree","op":"preorder","tree":["A","B","C",null,"D","E","F"],"pauseAt":["tree-first-visit"]}' AS JSON),'原创 · bt-03',NULL,'PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把先序当层序','访问 `B` 后必须完成 `B` 的子树，访问 `D`，才能进入 `A` 的右子树 `C`；不能先把同层 `B`、`C` 都输出。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'孩子抢在子树根前','先序进入 `B` 时先访问 `B`，再进入其右孩子 `D`。选项将 `D` 放在 `B` 前面。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把根延后访问','先序先访问整棵树的根 `A`，不能在 `B`、`D` 之后才访问 `A`。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'先确认先序中根的访问位置。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'进入一个子树后，完整处理这个子树，再回到父结点转向另一侧。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'另取根 `X`、左右叶 `Y`、`Z` 的小树，先写三者先序，再把叶替换成子树，规则仍相同。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 二叉树待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','同一棵树改为后序遍历，访问序列是？',CAST('[{"label":"A","content":"`D,B,C,E,F,A`"},{"label":"B","content":"`B,D,E,F,C,A`"},{"label":"C","content":"`D,B,E,F,C,A`"},{"label":"D","content":"`A,B,D,C,E,F`"}]' AS JSON),'C','后序先完成左右子树再访问根。`B` 子树输出 `D`、`B`，`C` 子树输出 `E`、`F`、`C`，最后访问 `A`，得到 `D`、`B`、`E`、`F`、`C`、`A`。',3,'PENDING','树形保持不变，先序改为后序。','同一棵树的后序访问',CAST('{"scene":"binary-tree","op":"postorder","tree":["A","B","C",null,"D","E","F"],"pauseAt":["tree-first-visit"]}' AS JSON));

-- bt-04
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('根 `A` 的左右孩子为 `B`、`C`；`B` 的左右孩子为 `D`、`E`；`C` 只有右孩子 `F`；`D`、`E`、`F` 均为叶。后序遍历刚访问完 `D`，下一个访问的结点是？','SINGLE_CHOICE',@bt_course,3,'`B` 的后序过程是先 `D`、再 `E`、最后 `B`。`D` 返回 `B` 时，接下来先进入并访问右孩子 `E`，因此选 `C`。完整序列为 `D`、`E`、`B`、`F`、`C`、`A`。','后序递归与返回',2,1,0,0,'AI_GENERATED','m5-draft:bt-04','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'`B`','A',0,1),
(@bt_q,'`C`','B',0,2),
(@bt_q,'`E`','C',1,3),
(@bt_q,'`A`','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'`B` 的后序过程是先 `D`、再 `E`、最后 `B`。`D` 返回 `B` 时，接下来先进入并访问右孩子 `E`，因此选 `C`。完整序列为 `D`、`E`、`B`、`F`、`C`、`A`。','AI_GENERATED','后序访问与返回调用者',CAST('{"scene":"binary-tree","op":"postorder","tree":["A","B","C","D","E",null,"F"],"pauseAt":["tree-first-visit"]}' AS JSON),'原创 · bt-04',NULL,'PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'返回调用者等于立即访问调用者','`D` 完成后返回 `B` 的调用，但 `B` 的右子树 `E` 尚未访问，后序不能立即输出 `B`。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'提前跳到另一棵子树','`A` 的左子树 `B` 还未完成，不能转入 `A` 的右子树 `C`。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把递归返回当回到根','返回的是直接调用者 `B`，而且 `B` 与 `A` 都还有待处理部分；整棵树的根 `A` 最后访问。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'区分返回某个调用与访问该调用对应的结点。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'检查直接调用者是否还有未处理的另一侧子树。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'另取一个有左右叶的结点 `X`：左叶结束之后，列出右叶与 `X` 的待办次序。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 二叉树待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','保持同一棵树与后序遍历，刚访问完 `E` 后，下一个访问谁？',CAST('[{"label":"A","content":"`B`"},{"label":"B","content":"`C`"},{"label":"C","content":"`F`"},{"label":"D","content":"`A`"}]' AS JSON),'A','`D`、`E` 已使 `B` 的左右子树全部完成，接下来访问 `B`；此时才完成 `A` 的左子树。',3,'PENDING','观察点由访问 `D` 后改为访问 `E` 后。','后序完成左右子树后访问 `B`',CAST('{"scene":"binary-tree","op":"postorder","tree":["A","B","C","D","E",null,"F"],"pauseAt":["tree-first-visit"]}' AS JSON));

-- bt-05
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('各结点标识互异。某二叉树先序为 `A,B,D,E,C,F`，中序为 `D,B,E,A,C,F`。其后序遍历是？','SINGLE_CHOICE',@bt_course,3,'先序首项 `A` 是根；中序在 `A` 两侧分出 `D`、`B`、`E` 和 `C`、`F`。左子树先序 `B`、`D`、`E`，故 `B` 左 `D` 右 `E`，后序 `D`、`E`、`B`；右子树 `C` 的右孩子为 `F`，后序 `F`、`C`。最后加 `A`，得到 `D`、`E`、`B`、`F`、`C`、`A`。','由先序与中序重建',2,1,0,0,'AI_GENERATED','m5-draft:bt-05','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'`D,E,F,B,C,A`','A',0,1),
(@bt_q,'`D,E,B,F,C,A`','B',1,2),
(@bt_q,'`A,B,D,E,C,F`','C',0,3),
(@bt_q,'`D,B,E,F,C,A`','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'先序首项 `A` 是根；中序在 `A` 两侧分出 `D`、`B`、`E` 和 `C`、`F`。左子树先序 `B`、`D`、`E`，故 `B` 左 `D` 右 `E`，后序 `D`、`E`、`B`；右子树 `C` 的右孩子为 `F`，后序 `F`、`C`。最后加 `A`，得到 `D`、`E`、`B`、`F`、`C`、`A`。','AI_GENERATED','重建后的树与后序',CAST('{"scene":"binary-tree","op":"postorder","tree":["A","B","C","D","E",null,"F"],"pauseAt":["tree-first-visit"]}' AS JSON),'原创 · bt-05',NULL,'PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把左右子树交叉处理','中序以 `A` 分成左侧 `D`、`B`、`E` 与右侧 `C`、`F`。左子树必须完整输出 `D`、`E`、`B`，再处理右子树 `F`、`C`。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'直接复用先序','后序的根 `A` 最后访问，选项仍从 `A` 开始。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把左子树当中序','左子树根为 `B`，后序应先 `D`、`E` 再 `B`，不能按中序 `D`、`B`、`E` 输出。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'先序首项确定当前子树的根。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'在当前中序片段按根切分左右部分，再按各部分长度切分先序。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'例如先序 `X,Y,Z`、中序 `Y,X,Z`，先定位根 `X`，再对左右两个片段递归；复杂树只需重复这一步。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 二叉树待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','各标识互异的另一棵树先序为 `P,Q,R,S`，中序为 `Q,R,P,S`。后序遍历是？',CAST('[{"label":"A","content":"`Q,R,S,P`"},{"label":"B","content":"`R,S,Q,P`"},{"label":"C","content":"`P,Q,R,S`"},{"label":"D","content":"`R,Q,S,P`"}]' AS JSON),'D','`P` 为根，左中序 `Q`、`R`、左先序 `Q`、`R`，故 `Q` 的右孩子为 `R`；右子树只有 `S`。后序为 `R`、`Q`、`S`、`P`。',3,'PENDING','更换为另一组合法先序和中序，要求独立重建后序。','新树重建后的后序',CAST('{"scene":"binary-tree","op":"postorder","tree":["P","Q","S",null,"R"],"pauseAt":["tree-first-visit"]}' AS JSON));

-- bt-06
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('一棵非空二叉树有 7 个度为 2 的结点、4 个度为 1 的结点。叶结点有多少个？这里度指孩子数。','SINGLE_CHOICE',@bt_course,3,'记叶数为 `n0`，单孩子结点数为 `n1`，双孩子结点数为 `n2`。边数既是 `n1+2*n2`，又是 `n0+n1+n2-1`，相等得 `n0=n2+1`。代入 7 得 8，4 个单孩子结点不影响结果；总结点数为 19。','二叉树结点度数性质',2,1,0,0,'AI_GENERATED','m5-draft:bt-06','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'7 个','A',0,1),
(@bt_q,'8 个','B',1,2),
(@bt_q,'11 个','C',0,3),
(@bt_q,'12 个','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'记叶数为 `n0`，单孩子结点数为 `n1`，双孩子结点数为 `n2`。边数既是 `n1+2*n2`，又是 `n0+n1+n2-1`，相等得 `n0=n2+1`。代入 7 得 8，4 个单孩子结点不影响结果；总结点数为 19。','AI_GENERATED',NULL,NULL,'原创 · bt-06',NULL,'PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'漏掉树根带来的差额','用边数等于总结点数减一列式后，可得叶数比双孩子结点数多一，不能直接取 7。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把所有分支结点当叶','7 加 4 得到的是非叶结点数；单孩子结点数会出现在边数和结点数两侧并抵消。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把分支数再加一','应比较叶与双孩子结点，不能将 4 个单孩子结点也加进叶数。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'从每个结点的孩子数统计整棵树的边。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'另一种统计边的方法是用总结点数减一，比较两个表达式。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'在一棵根带两个叶的小树上分别数结点和边，再思考若插入一个单孩子结点，两种计数如何同时变化。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 二叉树待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','另一棵非空二叉树有 3 个度为 2 的结点和 2 个度为 1 的结点，总结点数是多少？',CAST('[{"label":"A","content":"6 个"},{"label":"B","content":"7 个"},{"label":"C","content":"8 个"},{"label":"D","content":"9 个"}]' AS JSON),'D','叶数为 3 加 1，即 4；总结点数为 4 加 2 加 3，即 9。',3,'PENDING','双孩子与单孩子结点数量均改变，并改问总结点数。',NULL,NULL);

-- bt-07
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('完全二叉树有 10 个结点，按从上到下、每层从左到右编号 1 至 10，根在第 1 层。第 3 层从左数第 2 个结点的孩子情况是？','SINGLE_CHOICE',@bt_course,3,'第 3 层从编号 4 开始，第 2 个是编号 5。按 1 基编号，它的左右孩子位置分别是 10 和 11；树只有编号 1 至 10，所以只有左孩子。','完全二叉树的孩子位置',2,1,0,0,'AI_GENERATED','m5-draft:bt-07','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'没有孩子','A',0,1),
(@bt_q,'只有左孩子','B',1,2),
(@bt_q,'只有右孩子','C',0,3),
(@bt_q,'有两个孩子','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'第 3 层从编号 4 开始，第 2 个是编号 5。按 1 基编号，它的左右孩子位置分别是 10 和 11；树只有编号 1 至 10，所以只有左孩子。','AI_GENERATED',NULL,NULL,'原创 · bt-07',NULL,'PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'把未填满最底层当都无孩子','第 3 层编号为 4、5、6、7，目标是 5；其左孩子编号 10，仍在范围内。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'弄反完全树的补位方向','完全树最底层从左到右补齐，目标若只有一个孩子，只可能先有左孩子。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'没有检查右孩子是否越界','目标编号 5 的右孩子编号 11，超过现有 10 个结点，故不存在。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'先把目标的层次位置换成层序编号。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'根从 1 编号时，分别检查目标的左、右孩子编号是否超过总结点数。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'一棵只有 4 个结点的完全树中，编号 2 的左右孩子位置都可写出，但只有落在已有编号范围内的位置才存在。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 二叉树待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','在同样的完全二叉树中再按层序添加第 11 个结点。第 3 层从左数第 2 个结点现在有几个孩子？',CAST('[{"label":"A","content":"0 个"},{"label":"B","content":"仅右侧 1 个"},{"label":"C","content":"仅左侧 1 个"},{"label":"D","content":"2 个"}]' AS JSON),'D','目标仍是编号 5，左右孩子 10、11 现在均存在，所以有 2 个孩子。',3,'PENDING','总结点数由 10 增为 11，编号和目标位置保持不变。',NULL,NULL);

-- bt-08
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('各标识互异的二叉树先序为 `A,B,C`，后序为 `C,B,A`。共有多少种不同的有序二叉树满足这两个序列？左右位置不同算不同树。','SINGLE_CHOICE',@bt_course,3,'`A` 是根。若 `A` 有两个孩子，`B`、`C` 将分别占两个单结点子树，后序会是 `B`、`C`、`A`，与题目矛盾。故 `A` 只有孩子 `B`，`B` 只有孩子 `C`。两条连接分别可取左或右，共 2 乘 2 等于 4 种。','先序与后序的唯一性',2,1,0,0,'AI_GENERATED','m5-draft:bt-08','DRAFT');
SET @bt_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order) VALUES
(@bt_q,'1 种','A',0,1),
(@bt_q,'2 种','B',0,2),
(@bt_q,'3 种','C',0,3),
(@bt_q,'4 种','D',1,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id) VALUES(@bt_q,@bt_kp);
SET @bt_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@bt_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@bt_q,1,'PENDING',@bt_fp,@bt_kp,'`A` 是根。若 `A` 有两个孩子，`B`、`C` 将分别占两个单结点子树，后序会是 `B`、`C`、`A`，与题目矛盾。故 `A` 只有孩子 `B`，`B` 只有孩子 `C`。两条连接分别可取左或右，共 2 乘 2 等于 4 种。','AI_GENERATED',NULL,NULL,'原创 · bt-08',NULL,'PENDING');
SET @bt_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'误以为两种序列一定唯一','先序与后序不能区分单孩子在左侧还是右侧；这里 `A`、`B` 都只有一个孩子。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'只考虑其中一条连接','`A` 到 `B` 与 `B` 到 `C` 的左右位置可以独立选择，不能只给整条链选择一个共同方向。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @bt_content,id,'漏掉一类混合方向','两条单孩子连接各有两种位置，四种组合均产生相同先、后序，没有理由排除其中一种。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@bt_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,1,'先确定根，再检查它能否同时有两个非空孩子。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,2,'比较先序第二项与后序倒数第二项，确定根下面的子树范围。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@bt_content,3,'画一个单孩子局部结构，比较把孩子从左移到右，先序与后序能否区分；再分别检查每条连接。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@bt_q,'VARIANT','P27 二叉树待审核草稿变式',0);
SET @bt_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@bt_asset,'SINGLE_CHOICE','改为只有 `A`、`B` 两个结点，先序 `A,B`，后序 `B,A`。符合条件的有序二叉树有几种？',CAST('[{"label":"A","content":"1 种"},{"label":"B","content":"2 种"},{"label":"C","content":"3 种"},{"label":"D","content":"4 种"}]' AS JSON),'B','`A` 为根、`B` 为其唯一孩子；`B` 可为左孩子或右孩子，两种有序树的先序和后序分别相同。',3,'PENDING','结点数由 3 减为 2，其余约定相同。',NULL,NULL);

