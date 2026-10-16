ALTER TABLE question MODIFY visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性：PUBLIC/PRIVATE/DRAFT';
SET @m5_course=(SELECT id FROM course WHERE content_key='cs408-data-structures' AND deleted=0);

-- ll-01
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-linear-lists' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('长度为 5 的顺序表有足够空闲空间。若在第 1 个位置插入一个元素，为保持原有元素的相对顺序，必须移动几个原有元素？','SINGLE_CHOICE',@m5_course,3,'插入位序为 i 时，原第 i 至第 n 个元素各后移一格，共 n-i+1 个。本题 i=1、n=5，所以原第 1 至第 5 个都移动，共 5 个。','顺序表',2,1,0,0,'AI_GENERATED','m5-draft:ll-01','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'0 个','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'1 个','B',0,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'4 个','C',0,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'5 个','D',1,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'插入位序为 i 时，原第 i 至第 n 个元素各后移一格，共 n-i+1 个。本题 i=1、n=5，所以原第 1 至第 5 个都移动，共 5 个。','AI_GENERATED',NULL,NULL,'原创 · ll-01',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把表头当表尾','在第 1 个位置插入会占用原首元素的位置，原有 5 个元素都要后移。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'只移动原首元素','原首元素后移后还会占用第二个元素的位置，必须依次移动其余元素。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'漏掉最后一个元素','原第 5 个元素也要后移到第 6 个位置，否则会被覆盖。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'先区分插入位置与原表长，想想哪个已有元素会被覆盖。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'从末尾往前看，每个原有元素的下标是否都发生变化。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如长度为 2 的表在最前面插入，新元素之前要给两个旧元素分别腾出位置。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','长度为 5 的顺序表仍有空闲空间，改为在第 3 个位置插入一个元素，需要移动几个原有元素？',CAST('[{"label":"A","content":"1 个"},{"label":"B","content":"2 个"},{"label":"C","content":"3 个"},{"label":"D","content":"5 个"}]' AS JSON),'C','原第 3、4、5 个元素后移，共移动 5-3+1=3 个。',3,'PENDING','插入位序由 1 改为 3，其余条件不变。',NULL,NULL);

-- ll-02
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-linear-lists' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('长度为 6 的顺序表删除第 4 个元素，为保持其余元素的相对顺序，需要移动几个元素？','SINGLE_CHOICE',@m5_course,3,'删除第 i 个元素需要前移原第 i+1 至第 n 个元素，共 n-i 个；这里为 6-4=2 个。','顺序表',2,1,0,0,'AI_GENERATED','m5-draft:ll-02','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'1 个','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'2 个','B',1,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'3 个','C',0,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'4 个','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'删除第 i 个元素需要前移原第 i+1 至第 n 个元素，共 n-i 个；这里为 6-4=2 个。','AI_GENERATED',NULL,NULL,'原创 · ll-02',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'只数被删元素','被删元素不计入移动次数；后面的第 5、6 个元素要前移。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把被删元素也计入移动','把原第 4、5、6 个元素都算成移动，才会得到 3；被删的第 4 个元素不计入移动，只有第 5、6 个前移。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把删除位序当作移动数','4 是被删元素的位序。直接把它当移动数会得到 4；实际只需移动它后面的第 5、6 个元素。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'删除后空出的是哪个逻辑位置？','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'只统计删除位置后面的元素，它们各前移一格。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如 4 个元素删除第 2 个，前移的是原第 3、4 个，而不是前两个。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','长度为 6 的顺序表删除表尾（第 6 个元素），保持其余元素相对顺序。需要移动几个其余元素？',CAST('[{"label":"A","content":"0 个"},{"label":"B","content":"1 个"},{"label":"C","content":"5 个"},{"label":"D","content":"6 个"}]' AS JSON),'A','表尾之后没有元素，无需填补中间空位；只需把长度减一，移动 6-6=0 个其余元素。',3,'PENDING','删除位置由表中第 4 个改为表尾，检验没有后继元素的边界。',NULL,NULL);

-- ll-03
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-linear-lists' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('C 数组 data 依次存放线性表的 8、3、5、1。逻辑上第 3 个元素位于哪个数组下标？','SINGLE_CHOICE',@m5_course,3,'逻辑第 i 个元素对应 data[i-1]；第 3 个元素 5 在 data[2]。','位序与下标',2,1,0,0,'AI_GENERATED','m5-draft:ll-03','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'1','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'2','B',1,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'3','C',0,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'4','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'逻辑第 i 个元素对应 data[i-1]；第 3 个元素 5 在 data[2]。','AI_GENERATED',NULL,NULL,'原创 · ll-03',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把第三个误数成第二个','数组下标 1 对应逻辑第 2 个元素 3。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把位序当下标','逻辑位序从 1 起，C 数组下标从 0 起，第 3 个应减 1。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把元素个数当下标','下标 4 已越过这 4 个元素的有效范围 0..3。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'先写出逻辑第 1 个元素对应的数组下标。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'每往后一个逻辑位置，下标也加一；对照两列数字。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如逻辑第 2 个元素存在 data[1]，继续往后一个元素时下标如何变化？','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','C 数组 data 依次存放线性表的 8、3、5、1，数组下标从 0 开始。data[2] 存放的是逻辑上第几个元素？',CAST('[{"label":"A","content":"第 1 个"},{"label":"B","content":"第 2 个"},{"label":"C","content":"第 3 个"},{"label":"D","content":"第 4 个"}]' AS JSON),'C','下标 0 对应逻辑第 1 个，下标 2 对应逻辑第 2+1=3 个元素，即 5。',3,'PENDING','从由逻辑位序求下标改为由下标反求逻辑位序，数据不变。',NULL,NULL);

-- ll-04
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-linear-lists' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('带头结点的单链表 L 当前为 4→7→9。在第 1 个数据位置插入新结点 s，令 s 成为首元结点。插入前 p 应指向哪里？','SINGLE_CHOICE',@m5_course,3,'第 1 个数据结点的前驱是头结点，所以 p=L；令 s->next=p->next，再令 p->next=s。','带头结点单链表',2,1,0,0,'AI_GENERATED','m5-draft:ll-04','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'头结点','A',1,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'原首元结点 4','B',0,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'原第 2 个数据结点 7','C',0,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'NULL','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'第 1 个数据结点的前驱是头结点，所以 p=L；令 s->next=p->next，再令 p->next=s。','AI_GENERATED','演示选定前驱后的插入结果',CAST('{"scene":"linked-list","op":"insert","values":[4,7,9],"i":1,"e":6,"pauseAt":[]}' AS JSON),'原创 · ll-04',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把插入前驱看成原首元','若 p 指向原首元结点，s 接在 4 后面，成为第 2 个数据结点。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'前驱多走一步','7 是原第 2 个数据结点，接在它后面会插得更靠后。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'忽略头结点','带头结点时第 1 个数据位置的前驱是头结点，p 不应为 NULL。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'先找新结点插入后紧接着的后继是谁。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'插入第 i 个位置，要让 p 停在原第 i-1 个结点；带头结点时它记第 0 个。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如在 甲→乙 前插入新首元，连接位置在头结点与甲之间。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','同一带头结点链表 4→7→9，改为在第 3 个数据位置插入 s。插入前 p 应指向哪里？',CAST('[{"label":"A","content":"头结点"},{"label":"B","content":"原首元结点 4"},{"label":"C","content":"原第 2 个数据结点 7"},{"label":"D","content":"原第 3 个数据结点 9"}]' AS JSON),'C','第 3 个位置的前驱是原第 2 个数据结点 7。',3,'PENDING','插入位序由 1 改为 3，其余结构不变。','演示选定前驱后的插入结果',CAST('{"scene":"linked-list","op":"insert","values":[4,7,9],"i":3,"e":6,"pauseAt":[]}' AS JSON));

-- ll-05
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-linear-lists' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('带头结点的单链表中，p 指向某数据结点，其后继为 q。要把新结点 s 插在 p 与 q 之间，哪组指针赋值顺序正确？','SINGLE_CHOICE',@m5_course,3,'先执行 s->next=p->next 保存 q，再执行 p->next=s；从头结点出发变为 p→s→q。','单链表插入赋值顺序',2,1,0,0,'AI_GENERATED','m5-draft:ll-05','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'先 p->next=s，再 s->next=p->next','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'先 s->next=p->next，再 p->next=s','B',1,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'先 p->next=q->next，再 s->next=q','C',0,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'先 s->next=p，再 p->next=s','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'先执行 s->next=p->next 保存 q，再执行 p->next=s；从头结点出发变为 p→s→q。','AI_GENERATED','停在赋值顺序出错的一刻',CAST('{"scene":"linked-list","op":"insert","values":[2,4,8],"i":2,"e":6,"pauseAt":["insert-order"]}' AS JSON),'原创 · ll-05',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'先改前驱丢失后继','先令 p->next=s 后，再读 p->next 得到的是 s 自己，会令 s->next=s，原 q 从头结点不可达。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'跳过原后继','把 p 所指结点的 next 改为 q->next 会跳过 q；再令 s->next=q 也没有把 s 接到 p 后面。指针变量 p 本身没有移动。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把新结点指回前驱','s->next=p 会形成回指，无法按原顺序接上 q。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'画出插入前 p→q，先问哪条旧连接必须保留。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'逐句跟踪赋值：每一步后，旧后继的地址保存在哪里？再读 p->next 时，它与赋值前是否仍相同？','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如 2→4 中插入 3，若先让 2 指向 3，再从 2 读取后继，就只能读到 3。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','同一带头结点链表中，改为把 s 插在尾结点 p 之后（原 p->next=NULL）。哪组赋值顺序仍正确？',CAST('[{"label":"A","content":"先 p->next=s，再 s->next=p->next"},{"label":"B","content":"先 s->next=p->next，再 p->next=s"},{"label":"C","content":"只令 s->next=p，不改 p"},{"label":"D","content":"只令 p->next=s，不初始化 s->next"}]' AS JSON),'B','仍先令 s->next 取原 p->next，即 NULL，再令 p->next=s；这样新尾结点的指针域确定为空。',3,'PENDING','p 由中间结点改为尾结点，原后继从 q 改为 NULL。','在尾部接入新结点',CAST('{"scene":"linked-list","op":"insert","values":[2,4,8],"i":4,"e":6,"pauseAt":["insert-order"]}' AS JSON));

-- ll-06
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-linear-lists' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('带头结点的单链表 2→4→8 中，p 指向 2，q=p->next 指向 4。要删除 q 且保留后继，释放 q 前必须怎样更新链接？','SINGLE_CHOICE',@m5_course,3,'先保存 q= p->next，令 p->next=q->next，再 free(q)；原结点 8 仍可达。','单链表删除',2,1,0,0,'AI_GENERATED','m5-draft:ll-06','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'令 p->next=NULL','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'令 q->next=p','B',0,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'令 p->next=q->next','C',1,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'令 p=q->next','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'先保存 q= p->next，令 p->next=q->next，再 free(q)；原结点 8 仍可达。','AI_GENERATED','先接后继再释放',CAST('{"scene":"linked-list","op":"delete","values":[2,4,8],"i":2,"pauseAt":["delete-relink"]}' AS JSON),'原创 · ll-06',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'截断剩余链表','令 p->next=NULL 会让 8 从头结点不可达。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'形成回指','q->next=p 会让待释放的 q 指回 p，没把 p 正确接到 8。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'只移动局部变量','令 p=q->next 只改指针变量 p，不会修改结点 2 的 next 域。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'删除结点后，从头结点出发应能直接走到哪个后继？','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'分别画出改变指针变量与改变结点 next 域后的图，沿头结点出发的路径检查哪些结点仍可达。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如 甲→乙→丙 删除乙，应让甲直接指向丙，然后才能释放乙。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','带头结点的单链表为 2→4→8→NULL，p 指向 4，q=p->next 指向尾结点 8。正确删除并释放 q 后，p->next 的值是什么？',CAST('[{"label":"A","content":"头结点 L"},{"label":"B","content":"结点 4 的地址"},{"label":"C","content":"已释放结点 8 的地址"},{"label":"D","content":"NULL"}]' AS JSON),'D','原 q->next=NULL，删除前令 p->next=q->next，再释放 q；结点 4 成为尾结点，其 next 为 NULL。',3,'PENDING','删除对象由中间结点改为尾结点，检验后继为 NULL 的重接边界。','删除尾结点后的链接',CAST('{"scene":"linked-list","op":"delete","values":[2,4,8],"i":3,"pauseAt":["delete-relink"]}' AS JSON));

-- ll-07
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-linear-lists' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('带头结点的单链表 L 原为 2→4→8。循环中所有数据结点的 next 都已反向，prev 指向 8。要让从 L 出发可访问完整的新链表，最后还必须执行什么？','SINGLE_CHOICE',@m5_course,3,'逆置循环结束后 prev 指向新的首元结点 8；必须令 L->next=prev，使头结点接上 8→4→2。','单链表原地逆置',2,1,0,0,'AI_GENERATED','m5-draft:ll-07','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'令 prev->next=L','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'令 L=prev','B',0,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'令 L->next=NULL','C',0,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'令 L->next=prev','D',1,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'逆置循环结束后 prev 指向新的首元结点 8；必须令 L->next=prev，使头结点接上 8→4→2。','AI_GENERATED','谁来接回新首元',CAST('{"scene":"linked-list","op":"reverse","values":[2,4,8],"pauseAt":["reverse-who-points-last"]}' AS JSON),'原创 · ll-07',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'新首元指回头结点并截断逆置链','循环后数据链为 8→4→2→NULL，头结点仍指向 2。令 8->next=L 会覆盖 8→4，得到 8→头结点→2→NULL，并未形成环；头结点也没有接到新首元 8。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'只改局部头指针','L 是指向头结点的变量；仅令 L=prev 会丢开原头结点，不符合带头结点结构。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'切断新链表','令头结点 next=NULL 会使所有数据结点从 L 不可达。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'区分数据结点间的指针反向与头结点的指针域。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'prev 已指向新的第 1 个数据结点，谁必须指向它？','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如头结点原先指向甲，甲与乙反向后新的首元是乙，头结点还需重新接到乙。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','带头结点的单链表 L 为空，L->next=NULL。逆置算法令 prev=NULL，从首元结点开始循环反转 next，循环结束后统一执行 L->next=prev。最终 L->next 指向哪里？',CAST('[{"label":"A","content":"头结点 L"},{"label":"B","content":"NULL"},{"label":"C","content":"新分配的数据结点"},{"label":"D","content":"已释放结点"}]' AS JSON),'B','空表没有数据结点，循环一次也不执行，prev 保持 NULL；最后 L->next=prev 仍为 NULL，保留原头结点。',3,'PENDING','将非空链表改为空表，不预先给出循环结束时 prev 的位置，检验零次循环的边界。',NULL,NULL);

-- ll-08
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-linear-lists' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('顺序表当前长度为 4、容量为 7，在表尾插入一个元素且不触发扩容。操作后长度和容量分别是多少？','SINGLE_CHOICE',@m5_course,3,'表尾插入使有效元素数 4→5；题干明确不扩容，容量仍为 7。','顺序表长度与容量',2,1,0,0,'AI_GENERATED','m5-draft:ll-08','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'4 和 7','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'5 和 5','B',0,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'5 和 7','C',1,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'7 和 7','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'表尾插入使有效元素数 4→5；题干明确不扩容，容量仍为 7。','AI_GENERATED',NULL,NULL,'原创 · ll-08',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把插入当成覆盖','表尾插入增加一个有效元素，长度应从 4 变成 5。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把容量当作当前长度','容量是已分配的可容纳上限，不会因加入一个元素自动缩到 5。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把长度改成容量','原有 4 个加上新元素只有 5 个有效元素，剩余 2 个槽仍空闲。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'分清已使用的元素个数与已分配的槽位数。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'不扩容时哪个量保持不变？表尾插入又使哪个量加一？','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如容量 3、长度 1 的表追加一项后，容量仍 3，而长度变 2。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','固定容量为 7 的顺序表当前长度为 7。插入规则是：满表时拒绝插入且不修改表，不自动扩容。此时尝试在表尾再插入一项，结果如何？',CAST('[{"label":"A","content":"插入成功，长度 8、容量 7"},{"label":"B","content":"插入成功，长度 8、容量 8"},{"label":"C","content":"拒绝插入，长度 7、容量 7"},{"label":"D","content":"覆盖原表尾，长度 7、容量 7"}]' AS JSON),'C','长度已经等于容量 7，按题设拒绝新增元素；长度和容量均保持 7，也不能用覆盖表尾代替插入。',3,'PENDING','将有空闲空间改为满表，明确固定容量的拒绝规则，检验下一次插入是否允许。',NULL,NULL);

-- ll-09
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-linear-lists' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('非空单链表只有头指针和尾指针，结点只含 next，没有前驱指针。要删除尾结点并保持尾指针正确，最坏情况下需要多少时间？','SINGLE_CHOICE',@m5_course,3,'尾指针能直接找到尾结点，却不能在单链表中反向找到其前驱；最坏需从头遍历到倒数第二个结点，耗时 O(n)。','单链表尾结点删除',2,1,0,0,'AI_GENERATED','m5-draft:ll-09','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'O(1)','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'O(log n)','B',0,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'O(n)','C',1,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'O(n²)','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'尾指针能直接找到尾结点，却不能在单链表中反向找到其前驱；最坏需从头遍历到倒数第二个结点，耗时 O(n)。','AI_GENERATED',NULL,NULL,'原创 · ll-09',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'误把尾指针等同于前驱指针','尾指针只指向待删结点；单链表要重设前驱的 next，仍须从头遍历找前驱。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'以为可折半寻找尾前驱','单链表不能随机访问中间结点，无法像有序数组那样折半定位前驱。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把一次遍历当成嵌套遍历','只需从头沿 next 走一遍找尾结点前驱，最坏时间与结点数成正比。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'删除尾结点后，谁的 next 必须变成 NULL？','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'已有尾指针能否直接找到它的前驱？沿单链表只能向哪个方向走？','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如 甲→乙→丙 且只有丙的地址，若没有 prev 域，必须从甲走到乙才能断开乙→丙。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','只把结构改为双向链表，仍同时保存头指针和尾指针；删除尾结点的最坏时间是多少？',CAST('[{"label":"A","content":"O(1)"},{"label":"B","content":"O(log n)"},{"label":"C","content":"O(n)"},{"label":"D","content":"O(n²)"}]' AS JSON),'A','尾结点的 prev 可直接定位前驱，改其 next 并更新尾指针即可，时间 O(1)。',3,'PENDING','结点从只有 next 的单链表改为含 prev 的双向链表，其余条件不变。',NULL,NULL);

-- ll-10
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-linear-lists' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('顺序表长度为 n，已预留空闲容量。只在表尾追加一个元素，且不触发扩容，这次操作的时间复杂度是多少？','SINGLE_CHOICE',@m5_course,3,'不扩容时直接写入 data[n] 并把长度加一，旧元素不移动，时间 O(1)。','顺序表操作复杂度',2,1,0,0,'AI_GENERATED','m5-draft:ll-10','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'O(1)','A',1,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'O(log n)','B',0,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'O(n)','C',0,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'O(n²)','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'不扩容时直接写入 data[n] 并把长度加一，旧元素不移动，时间 O(1)。','AI_GENERATED',NULL,NULL,'原创 · ll-10',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把定位末尾当作折半查找','顺序表保存当前长度，可直接用下标找到下一空位，不需查找。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把表头插入的搬移成本套到表尾','表尾追加时旧元素位置不变；题干也排除了扩容复制。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把一次写入误当作两层遍历','无需遍历任何旧元素，更没有嵌套循环。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'题干限定不扩容；哪些旧元素需要移动？','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'顺序表知道当前长度后，可直接定位下一空槽。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如长度为 3 的数组表且槽位充足，追加到 data[3] 只做一次写入。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','同一顺序表仍有空闲容量，改为在表头插入一个元素并保持顺序，最坏时间复杂度是多少？',CAST('[{"label":"A","content":"O(1)"},{"label":"B","content":"O(log n)"},{"label":"C","content":"O(n)"},{"label":"D","content":"O(n²)"}]' AS JSON),'C','原 n 个元素都要后移一格，移动次数与 n 成正比，因此为 O(n)。',3,'PENDING','插入位置由表尾改为表头，其余条件不变。',NULL,NULL);

-- qu-01
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-stacks-queues-arrays' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('空队列依次入队 1、2、3，出队两次后再入队 4。此时队头到队尾的元素顺序是什么？','SINGLE_CHOICE',@m5_course,3,'出队两次依次移走 1、2，只剩 3；4 从队尾进入，顺序为 3、4。','先进先出',2,1,0,0,'AI_GENERATED','m5-draft:qu-01','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'1、4','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'2、4','B',0,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'3、4','C',1,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'4、3','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'出队两次依次移走 1、2，只剩 3；4 从队尾进入，顺序为 3、4。','AI_GENERATED',NULL,NULL,'原创 · qu-01',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把已出队元素留在队头','1 已在第一次出队时离开队列，不会再次成为队头。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'只计算一次出队','前两次出队分别移走 1 和 2，队中剩 3。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把新入队元素放在队头','4 从队尾加入，不能排在已有元素 3 之前。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'队列只从队头出队、队尾入队。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'每一次操作后写下队头到队尾的完整顺序。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如 甲、乙入队后出队一次只剩乙，再入队丙得到乙、丙。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','从空队列开始，依次执行：入队 1，出队一次，入队 2，出队一次，入队 3，入队 4，出队一次。此时队头到队尾的元素顺序是什么？',CAST('[{"label":"A","content":"3、4"},{"label":"B","content":"4"},{"label":"C","content":"1、4"},{"label":"D","content":"队列为空"}]' AS JSON),'B','第一次出队移走 1，队列变空；第二次出队移走 2，再次变空；随后入队 3、4，最后出队移走新队头 3，只剩 4。',3,'PENDING','将两次出队提前到对应入队之后交错执行，并在入队 4 后再出队一次，检验多次清空与新队头。',NULL,NULL);

-- qu-02
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-stacks-queues-arrays' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('长度为 M=8 的循环数组队列保留一个空槽。front 指向队头、rear 指向下一空位。若 front=6、rear=2，队中有几个元素？','SINGLE_CHOICE',@m5_course,3,'元素占据下标 6、7、0、1；长度为 (2-6+8)%8=4。','循环队列长度',2,1,0,0,'AI_GENERATED','m5-draft:qu-02','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'2 个','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'3 个','B',0,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'4 个','C',1,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'6 个','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'元素占据下标 6、7、0、1；长度为 (2-6+8)%8=4。','AI_GENERATED',NULL,NULL,'原创 · qu-02',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'直接取 rear 的数值','rear=2 只是物理下标，不能代表队列元素个数。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'回绕时少算一个槽','从下标 6、7、0、1 共四个有效位置，rear=2 是下一空位。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'只看 front 的数值','front=6 是物理起点，不是元素数目；队列已跨过数组末端。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'循环队列可以从数组尾部回绕到下标 0。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'从 front 数到 rear 之前，或使用 (rear-front+M)%M。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如 M=5、front=4、rear=1 时，有下标 4、0 两个元素。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','循环数组队列长度 M=8，front 指向队头，rear 改为指向最后一个有效元素。用 count 区分满空：count=0 为空，count=M 为满。非空时元素占据从 front 到 rear 的连续循环区间（两端均计入）。若 front=6、rear=2，队中有几个元素？',CAST('[{"label":"A","content":"3 个"},{"label":"B","content":"4 个"},{"label":"C","content":"5 个"},{"label":"D","content":"6 个"}]' AS JSON),'C','有效下标为 6、7、0、1、2，共 5 个；非空时长度为 (rear-front+M)%M+1，不能照搬 rear 指下一空位时的公式。',3,'PENDING','保持数组长度和两个下标，只把 rear 由下一空位改为最后一个有效元素，配套用计数器区分满空。',NULL,NULL);

-- qu-03
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-stacks-queues-arrays' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('长度为 M=6 的循环数组队列用“保留一个空槽”区分队满与队空，最多可同时存放几个元素？','SINGLE_CHOICE',@m5_course,3,'该表示必须始终留下一个空槽，因此 M=6 时最多存 M-1=5 个元素。','循环队列容量',2,1,0,0,'AI_GENERATED','m5-draft:qu-03','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'4 个','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'5 个','B',1,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'6 个','C',0,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'7 个','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'该表示必须始终留下一个空槽，因此 M=6 时最多存 M-1=5 个元素。','AI_GENERATED',NULL,NULL,'原创 · qu-03',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'多预留了一个槽','只保留一个空槽，6 个物理位置仍可放 5 个元素。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把物理容量当有效容量','若 6 个槽全放满，front==rear 会与空队列状态混淆。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'超过物理数组长度','物理数组只有 6 个槽，不可能同时存放 7 个元素。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'想想队空与队满若都满足 front==rear，会怎样区分。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'保留一个空槽时，有效容量比数组长度少多少？','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如 M=3 的循环队列必须留 1 个空槽，因此最多放 2 个元素。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','循环数组队列长度 M=6，改用计数器 count 区分满空：count=0 为空，count=M 为满，不保留空槽。最多可同时存放几个元素？',CAST('[{"label":"A","content":"4 个"},{"label":"B","content":"5 个"},{"label":"C","content":"6 个"},{"label":"D","content":"7 个"}]' AS JSON),'C','计数器可以区分同样指针位置下的满与空，无需浪费一个槽，6 个物理位置均可存元素，有效容量为 M=6。',3,'PENDING','物理数组长度保持为 6，只把保留空槽改为用计数器区分满空。',NULL,NULL);

-- qu-04
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-stacks-queues-arrays' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('循环队列 M=5，front=1、rear=4，rear 指向下一空位。队列未满，执行一次 EnQueue(x) 后 rear 为多少？','SINGLE_CHOICE',@m5_course,3,'先把 x 写入 data[4]，再令 rear=(4+1)%5=0；front 仍为 1。','循环队列入队回绕',2,1,0,0,'AI_GENERATED','m5-draft:qu-04','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'0','A',1,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'1','B',0,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'3','C',0,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'4','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'先把 x 写入 data[4]，再令 rear=(4+1)%5=0；front 仍为 1。','AI_GENERATED',NULL,NULL,'原创 · qu-04',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把 rear 跳回 front','入队后的 rear 要按模 5 前进一格，从 4 到 0；front 保持 1。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'让 rear 反向移动','入队在尾部增加元素，rear 沿下标递增方向前进，不向 3 倒退。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'忘记推进 rear','x 写在原下标 4 后，rear 必须指向下一空位 0。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'rear 表示下一次可写的空位，写入后也要更新。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'循环数组从最后一个下标前进一步会回到哪里？','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如 M=4、rear=3 时入队后，新 rear=(3+1)%4。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','同样 M=5、front=1，只把入队前 rear 改为 3。执行一次 EnQueue(x) 后 rear 为多少？',CAST('[{"label":"A","content":"0"},{"label":"B","content":"1"},{"label":"C","content":"3"},{"label":"D","content":"4"}]' AS JSON),'D','把 x 写在 data[3]，随后 rear=(3+1)%5=4。',3,'PENDING','入队前 rear 由 4 改为 3，容量与操作不变。',NULL,NULL);

-- qu-05
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-stacks-queues-arrays' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('循环队列 M=5，front 指向队头元素、rear 指向下一空位；空队列初始 front=rear=3。向空队列入队 x 后，(front,rear) 应为哪一对？','SINGLE_CHOICE',@m5_course,3,'x 写入下标 3，front 仍为 3，rear 前进到 4，因此为 (3,4)。','循环队列空态入队',2,1,0,0,'AI_GENERATED','m5-draft:qu-05','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'(4,3)','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'(4,4)','B',0,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'(3,4)','C',1,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'(3,3)','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'x 写入下标 3，front 仍为 3，rear 前进到 4，因此为 (3,4)。','AI_GENERATED',NULL,NULL,'原创 · qu-05',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把队头也推进了','入队只改变尾部，front 仍指向刚写入 x 的位置 3。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'同时移动两个指针','front 和 rear 同时到 4 会再次满足空队列条件，错误地把含 x 的队列判空。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'忘记 rear 前进','写入 x 后 rear 要指向下一空位 4，不能继续与 front 相等。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'空队列的 front=rear；入队后新元素位于原 rear 指向的位置。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'队头是否需要移动？再看 rear 应指向哪里。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如空队列 front=rear=0，放入甲后队头仍在 0，rear 前进到 1。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','循环数组队列 M=5，front 指向队头元素，rear 指向下一空位，保留一个空槽区分满空；初始空队列 front=rear=4。入队 x 后 (front,rear) 为哪一对？',CAST('[{"label":"A","content":"(0,4)"},{"label":"B","content":"(4,0)"},{"label":"C","content":"(0,0)"},{"label":"D","content":"(4,4)"}]' AS JSON),'B','x 写入下标 4，front 保持 4，rear=(4+1)%5=0。',3,'PENDING','保留 front 指队头、rear 指下一空位及保留空槽的规则，只将初始空队列指针由 3 改为 4，检验回绕。',NULL,NULL);

-- qu-06
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-stacks-queues-arrays' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('用 in、out 两个栈实现队列：入队压入 in；出队时若 out 为空，先把 in 的元素全部逐个弹出并压入 out。起初 in 从底到顶为 1、2、3，out 为空。出队一次、再入队 4、再出队一次，两次出队值依次是什么？','SINGLE_CHOICE',@m5_course,3,'把 in 的 3、2、1 依次弹出压入 out 后，out 从底到顶为 3、2、1；第一次出队得 1。入队 4 到 in 时 out 仍有 2 在顶端，第二次出队得 2。','双栈实现队列',2,1,0,0,'AI_GENERATED','m5-draft:qu-06','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'1、2','A',1,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'1、4','B',0,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'3、2','C',0,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'3、4','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'把 in 的 3、2、1 依次弹出压入 out 后，out 从底到顶为 3、2、1；第一次出队得 1。入队 4 到 in 时 out 仍有 2 在顶端，第二次出队得 2。','AI_GENERATED',NULL,NULL,'原创 · qu-06',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'误以为新入队 4 会抢到队头','第一次转移后 out 里仍有 2、3，第二次出队先取 out 栈顶的 2，不会取刚进入 in 的 4。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'直接从 in 栈顶出队','out 为空时须先把 in 全部倒过去，使最早入队的 1 位于 out 栈顶。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把队列当栈使用','两次都取较晚入队的元素违反先进先出；转移到 out 的目的就是恢复队列顺序。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'先看 out 为空时转移全部元素会怎样改变上下顺序。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'画出第一次转移后 out 从底到顶的元素，再插入 4 到 in。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如 in 底甲顶乙倒入 out 后，out 顶为甲，先出队的是甲。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','用 in、out 两栈实现队列：入队压入 in；出队时仅当 out 为空，才把 in 全部逐个弹出并压入 out，然后从 out 弹出一个元素。初始 in 从底到顶为 1、2、3，out 为空。先出队一次，再入队 4，随后连续出队三次。四次出队值依次是什么？',CAST('[{"label":"A","content":"1、4、2、3"},{"label":"B","content":"3、2、1、4"},{"label":"C","content":"1、2、3、4"},{"label":"D","content":"1、2、4、3"}]' AS JSON),'C','第一次转移把 1 放到 out 栈顶，先后出队 1、2、3。第四次出队时 out 已空，才将 in 中的 4 转移到 out 并弹出，完整顺序为 1、2、3、4。',3,'PENDING','入队 4 后继续出队，跨过 out 清空的边界，触发第二次从 in 转移。',NULL,NULL);

-- qu-07
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-stacks-queues-arrays' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('带头结点的链队列中，front 指向保留的头结点；非空时 rear 指向最后一个数据结点，空队列约定 rear=front 且 front->next=NULL。当前队列只有一个数据结点 q。出队并释放 q 后，rear 应指向哪里？','SINGLE_CHOICE',@m5_course,3,'删除唯一的数据结点后，头结点仍在，front->next=NULL，rear 必须重新指向 front，表示空队列。','链队列最后元素出队',2,1,0,0,'AI_GENERATED','m5-draft:qu-07','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'原 q 的后继 NULL','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'已释放的 q','B',0,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'头结点 front','C',1,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'另建一个数据结点','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'删除唯一的数据结点后，头结点仍在，front->next=NULL，rear 必须重新指向 front，表示空队列。','AI_GENERATED',NULL,NULL,'原创 · qu-07',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把尾指针置 NULL 破坏该表示','题干明确约定空队列 rear=front。删除唯一数据结点后，rear 应回到保留的头结点；置 NULL 不符合本题的空队列表示。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'让尾指针悬空','q 已释放，rear 继续指向 q 会成为悬空指针，后续入队不安全。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'误以为清空队列须分配结点','头结点仍存在，队空只需让头结点 next=NULL 并让 rear 回到 front。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'出队后还有数据结点吗？头结点是否仍存在？','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'该表示下空队列时，尾指针应与哪个现存结点重合？','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如只有甲一个数据结点，删除甲后不能让 rear 继续指向已释放内存。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','带头结点的链队列中，front 指向保留的头结点，非空时 rear 指向最后一个数据结点，空队列约定 rear=front 且 front->next=NULL。当前有两个数据结点 q→r。只出队并释放 q 后，rear 应指向哪里？',CAST('[{"label":"A","content":"头结点 front"},{"label":"B","content":"已释放的 q"},{"label":"C","content":"剩余数据结点 r"},{"label":"D","content":"NULL"}]' AS JSON),'C','删除 q 后还有 r，r 仍是尾结点，rear 保持指向 r；只有删到空队列才重置为 front。',3,'PENDING','原队列由一个数据结点改为两个，只删除队头一次。',NULL,NULL);

-- st-01
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-stacks-queues-arrays' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('元素按 1、2、3、4 的顺序各入栈一次。下列哪个序列可能是其出栈顺序？','SINGLE_CHOICE',@m5_course,3,'要得到 2,1，先压入 1、2，依次弹出；再压入 3、4，依次弹出 4、3，恰好得到 2,1,4,3。','合法出栈序列',2,1,0,0,'AI_GENERATED','m5-draft:st-01','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'2,4,1,3','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'3,1,2,4','B',0,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'2,1,4,3','C',1,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'4,2,3,1','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'要得到 2,1，先压入 1、2，依次弹出；再压入 3、4，依次弹出 4、3，恰好得到 2,1,4,3。','AI_GENERATED','用候选序列逐步检查栈顶',CAST('{"scene":"stack","op":"check","seq":[2,1,4,3],"pauseAt":["stack-first-push"]}' AS JSON),'原创 · st-01',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'以为弹出 4 后可越过 3 取 1','弹出 2 后栈内留着 1；为取 4 要压入 3、4。弹出 4 后栈顶变为 3，不能跳过 3 取 1。','AI_GENERATED','用候选序列逐步检查栈顶',CAST('{"scene":"stack","op":"check","seq":[2,4,1,3],"pauseAt":["stack-buried"]}' AS JSON) FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'以为弹出 3 后可直接取 1','先弹出 3 后，栈中从底到顶是 1、2；下一目标 1 被 2 压住。','AI_GENERATED','用候选序列逐步检查栈顶',CAST('{"scene":"stack","op":"check","seq":[3,1,2,4],"pauseAt":["stack-buried"]}' AS JSON) FROM question_option WHERE question_id=@m5_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'以为弹出 4 后可直接取 2','弹出 4 后，栈顶仍是 3；下一步不能越过 3 取 2。','AI_GENERATED','用候选序列逐步检查栈顶',CAST('{"scene":"stack","op":"check","seq":[4,2,3,1],"pauseAt":["stack-buried"]}' AS JSON) FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'先记住入栈顺序固定，出栈只能取当前栈顶。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'逐个检查候选的下一目标：没入栈就继续压，已在栈内就看是否位于顶端。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如按甲、乙、丙入栈，为让乙先出栈，先压入甲、乙，弹出乙。接着要取丙时继续压入丙，再核对栈顶。对本题每个候选也逐步记录待入栈元素和栈顶。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','元素按 1、2、3、4 的顺序各入栈一次，栈最多同时容纳 2 个元素，允许入栈与出栈交错。下列哪个序列能在该容量内实现？',CAST('[{"label":"A","content":"3,2,1,4"},{"label":"B","content":"2,1,4,3"},{"label":"C","content":"4,3,2,1"},{"label":"D","content":"1,4,3,2"}]' AS JSON),'B','B 可先压入 1、2，再弹出 2、1；随后压入 3、4，再弹出 4、3，栈内最多 2 个元素。A、C、D 在首次输出 3、4 或输出 1 后再取 4 前，分别需要同时容纳 3、4、3 个元素，超出容量。',3,'PENDING','保持入栈顺序与元素数，只增加最多同时容纳 2 个元素的容量限制。',NULL,NULL);

-- st-02
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-stacks-queues-arrays' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('某顺序栈的 top 指向当前栈顶元素，空栈时 top=-1。连续入栈 3 个元素，再出栈 1 个元素后，top 的值是多少？','SINGLE_CHOICE',@m5_course,3,'top 从 -1 经三次入栈变为 0、1、2，再经一次出栈降为 1。','顺序栈栈顶下标',2,1,0,0,'AI_GENERATED','m5-draft:st-02','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'0','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'1','B',1,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'2','C',0,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'3','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'top 从 -1 经三次入栈变为 0、1、2，再经一次出栈降为 1。','AI_GENERATED',NULL,NULL,'原创 · st-02',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'多减了一次','入栈三次后 top=2，只出栈一次，所以 top=1。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'忘记出栈会使 top 下移','top=2 是三次入栈后、尚未出栈时的值。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把入栈次数直接当下标','数组下标从 0 起；三次入栈后的栈顶在下标 2。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'题干已指定 top 指向当前元素，不是下一个空位。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'从 -1 开始，入栈一次加 1，出栈一次减 1。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如空栈入栈两次后 top=1，再出栈一次就回到 0。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','某顺序栈的 top 指向下一个可写空位，空栈时 top=0，容量充足。连续入栈 3 个元素，再出栈 1 个元素后，top 的值是多少？',CAST('[{"label":"A","content":"0"},{"label":"B","content":"1"},{"label":"C","content":"2"},{"label":"D","content":"3"}]' AS JSON),'C','top 表示下一空位，也等于当前元素个数；由 0 入栈三次到 3，再出栈一次到 2。当前栈顶元素在下标 top-1=1。',3,'PENDING','操作次数保持不变，只把 top 的表示改为下一空位，并对应规定空栈初值为 0。',NULL,NULL);

-- st-03
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-stacks-queues-arrays' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('栈中从底到顶为 2、5、7。先执行 GetTop(S)（只读取不移除），再执行一次 Pop(S)。两次得到的元素依次是什么？','SINGLE_CHOICE',@m5_course,3,'GetTop(S) 返回 7 且栈不变；Pop(S) 随后弹出同一个栈顶 7。','取栈顶与出栈',2,1,0,0,'AI_GENERATED','m5-draft:st-03','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'5、7','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'7、5','B',0,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'5、5','C',0,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'7、7','D',1,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'GetTop(S) 返回 7 且栈不变；Pop(S) 随后弹出同一个栈顶 7。','AI_GENERATED',NULL,NULL,'原创 · st-03',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把第二个元素当成第一次栈顶','第一次取栈顶读到 7，不是下方的 5。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'误以为 GetTop 会弹出','GetTop 只读 7，不改变栈；紧接着 Pop 仍弹出 7。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把栈顶和下一个元素混淆','两次操作前栈顶都为 7；5 要等 7 真正出栈后才成为栈顶。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'确认 GetTop 与 Pop 是否改变栈。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'画出操作前后的栈，第一次读取后保持原样，再进行第二次操作。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如只有甲、乙且乙在顶端，先查看栈顶并不会让甲马上露出来。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','栈中从底到顶为 2、5、7。先执行 Pop(S)（弹出并返回栈顶元素），再执行 GetTop(S)（只读取不移除栈顶）。两次返回的元素依次是什么？',CAST('[{"label":"A","content":"5、7"},{"label":"B","content":"7、5"},{"label":"C","content":"7、7"},{"label":"D","content":"5、5"}]' AS JSON),'B','Pop 先返回并移除 7，栈变为底 2、5 顶；GetTop 随后返回 5，栈保持不变。',3,'PENDING','初始栈不变，只把 GetTop 与 Pop 的先后顺序交换。',NULL,NULL);

-- st-04
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-stacks-queues-arrays' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('用栈检查括号串 ([)]。从左到右读到第 3 个字符 )，尚未对这个字符执行任何栈操作时，栈顶是哪一个字符？','SINGLE_CHOICE',@m5_course,3,'读到第三个字符前，栈从底到顶为 (、[，因此栈顶是 [；当前 ) 与之不匹配。','括号匹配',2,1,0,0,'AI_GENERATED','m5-draft:st-04','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'(','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'[','B',1,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,')','C',0,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'栈已空','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'读到第三个字符前，栈从底到顶为 (、[，因此栈顶是 [；当前 ) 与之不匹配。','AI_GENERATED',NULL,NULL,'原创 · st-04',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把较早入栈的左括号当栈顶','读到 ) 前依次压入 ( 与 [，较晚压入的 [ 才在栈顶。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把当前输入字符当栈顶','当前 ) 尚未入栈；匹配检查取的是已有左括号栈顶。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'提前弹出了未匹配的左括号','前两个左括号都未遇到对应右括号，栈不为空。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'匹配检查只看最近尚未配对的左括号。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'逐字扫描前两个字符，把左括号按出现顺序压入栈。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如读完 ({ 后，下一右括号首先要与更晚压入的 { 比较。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','用栈检查括号串 ([])]。从左到右读到第 5 个字符 ]，尚未对这个字符执行任何栈操作时，栈顶是什么？',CAST('[{"label":"A","content":"("},{"label":"B","content":"["},{"label":"C","content":"]"},{"label":"D","content":"栈已空"}]' AS JSON),'D','前四个字符 ([]) 已匹配完毕：先用 ] 弹出 [，再用 ) 弹出 (。读到第五个字符 ] 时栈已空，该右括号没有可匹配的左括号。',3,'PENDING','在后续右括号到来前加入已成功匹配的括号对，检验弹栈后的状态。',NULL,NULL);

-- st-05
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-stacks-queues-arrays' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('按栈求值后缀表达式 3 4 + 2 * 的结果是多少？','SINGLE_CHOICE',@m5_course,3,'依次压入 3、4，遇 + 得到 7 并入栈；压入 2，遇 * 得到 7×2=14。','后缀表达式',2,1,0,0,'AI_GENERATED','m5-draft:st-05','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'9','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'11','B',0,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'14','C',1,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'24','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'依次压入 3、4，遇 + 得到 7 并入栈；压入 2，遇 * 得到 7×2=14。','AI_GENERATED',NULL,NULL,'原创 · st-05',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把乘号也当成加号','3 与 4 先相加为 7，随后应乘 2，而不是再加 2。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'按中缀优先级提前算 4×2','后缀表达式的运算顺序由操作符出现位置决定；+ 先作用于 3 与 4。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'忽略加号把三个数连乘','第一个操作符是 +，不能把 3 与 4 先相乘。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'后缀表达式遇到数字就入栈，遇到运算符才取栈顶两个数。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'从左到右处理，先看 + 出现时栈顶的两个值。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如 2 5 + 3 * 先得到 7，再拿 7 与 3 相乘。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','按栈求值后缀表达式 3 4 - 2 * 的结果是多少？',CAST('[{"label":"A","content":"-2"},{"label":"B","content":"2"},{"label":"C","content":"-5"},{"label":"D","content":"14"}]' AS JSON),'A','遇到 - 时先弹出的 4 是右操作数，后弹出的 3 是左操作数，计算 3-4=-1 并入栈；再压入 2，遇 * 得到 -1×2=-2。',3,'PENDING','操作数与符号位置不变，只将第一个运算符由加法改为减法，检验左右操作数次序。',NULL,NULL);

-- st-06
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-stacks-queues-arrays' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('空栈依次执行 Push(1)、Push(2)、Pop、Push(3)、Pop、Pop。三次 Pop 的返回值依次是什么？','SINGLE_CHOICE',@m5_course,3,'第一次 Pop 弹出 2；此后 Push(3)，第二次 Pop 弹出 3；最后弹出剩余的 1。','入栈与出栈操作追踪',2,1,0,0,'AI_GENERATED','m5-draft:st-06','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'1、2、3','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'2、1、3','B',0,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'2、3、1','C',1,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'3、2、1','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'第一次 Pop 弹出 2；此后 Push(3)，第二次 Pop 弹出 3；最后弹出剩余的 1。','AI_GENERATED','用候选序列逐步检查栈顶',CAST('{"scene":"stack","op":"check","seq":[2,3,1],"pauseAt":["stack-first-push"]}' AS JSON),'原创 · st-06',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把栈当成队列','第一次 Pop 取的是后压入的 2，而非最早入栈的 1。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'忘记 Push(3) 改变栈顶','第一次弹出 2 后又压入 3；第二次应弹出 3，而不是 1。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='B' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把后来的 3 提前到第一次出栈','第一次 Pop 发生在 Push(3) 之前，不可能先弹出 3。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'逐步画出栈，不要只看所有 Push 的总顺序。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'在每一次 Pop 发生的时刻记录当时的栈顶。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如 Push(甲)、Push(乙)、Pop、Push(丙)、Pop 会先后得到乙和丙。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','仍从空栈开始，改为先 Push(1)、Push(2)、Push(3)，随后连续 Pop 三次。返回值依次是什么？',CAST('[{"label":"A","content":"1、2、3"},{"label":"B","content":"2、3、1"},{"label":"C","content":"3、2、1"},{"label":"D","content":"3、1、2"}]' AS JSON),'C','三个元素全部压入后，栈顶依次为 3、2、1。',3,'PENDING','第一次 Pop 延后到 Push(3) 之后，其余元素及操作次数不变。','用候选序列逐步检查栈顶',CAST('{"scene":"stack","op":"check","seq":[3,2,1],"pauseAt":["stack-first-push"]}' AS JSON));

-- st-07
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-stacks-queues-arrays' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('1、2、3 各按顺序入栈一次，每个元素也各出栈一次。共有多少种不同的合法出栈序列？','SINGLE_CHOICE',@m5_course,3,'3 个元素有 6 个排列，其中 3,1,2 不可达；其余 1,2,3、1,3,2、2,1,3、2,3,1、3,2,1 共 5 种。','合法出栈序列计数',2,1,0,0,'AI_GENERATED','m5-draft:st-07','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'4 种','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'5 种','B',1,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'6 种','C',0,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'8 种','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'3 个元素有 6 个排列，其中 3,1,2 不可达；其余 1,2,3、1,3,2、2,1,3、2,3,1、3,2,1 共 5 种。','AI_GENERATED',NULL,NULL,'原创 · st-07',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'漏算一种可达顺序','除 1,2,3、1,3,2、2,1,3、3,2,1 外，2,3,1 也可由先弹 2、再弹 3、最后弹 1 得到。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把所有排列都算作合法','3,1,2 不合法：弹出 3 后栈顶是 2，1 被压在下面。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把过程当作三个独立二选一','若给三个元素各安排一个独立的二选一决策，会误算成 2³=8。压栈与出栈受输入顺序及当前栈状态约束，不能独立选择；必须检查每个完整输出是否可达。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'先列出 3 个元素的所有排列，再逐个检查栈顶限制。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'固定输入顺序后，某个候选若要求越过栈顶，就排除它。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如两元素 1、2 只有 1,2 与 2,1 两种合法输出。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','初始为空的栈最多同时容纳 2 个元素。1、2、3 各按顺序入栈一次，每个元素也各出栈一次，入栈与出栈可以交替进行。共有多少种不同的合法出栈序列？',CAST('[{"label":"A","content":"3 种"},{"label":"B","content":"4 种"},{"label":"C","content":"5 种"},{"label":"D","content":"6 种"}]' AS JSON),'B','逐个检查三个元素的 6 个排列，同时满足后进先出与容量限制的序列为 1,2,3、1,3,2、2,1,3、2,3,1，共 4 种，选 B。其中 2,3,1 可通过压入 1、2，弹出 2，压入 3，弹出 3、1 得到，栈中最多同时有 2 个元素。以 3 开头的两个排列都需在首次出栈前同时压入 1、2、3，超过容量，均不可达。',3,'PENDING','保留原题的三个元素及入栈顺序，增加栈最多同时容纳 2 个元素的限制。',NULL,NULL);

-- st-08
SET @m5_kp=(SELECT id FROM knowledge_point WHERE content_key='408-stacks-queues-arrays' AND deleted=0);
INSERT INTO question(content,question_type,course_id,difficulty,analysis,tags,score,status,create_by,deleted,source_type,source_reference,visibility)
VALUES('数组容量为 5 的顺序栈用 top 指向当前栈顶元素，空栈 top=-1。若 top=4，再执行一次 Push，正确结果是什么？','SINGLE_CHOICE',@m5_course,3,'有效下标为 0 至 4。top=4 表示五个槽均已占用；Push 应报告满栈，不修改栈与 top。','顺序栈满态',2,1,0,0,'AI_GENERATED','m5-draft:st-08','DRAFT');
SET @m5_q=LAST_INSERT_ID();
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'成功，top 变 5','A',0,1);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'栈满，Push 应拒绝且 top 保持 4','B',1,2);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'成功，覆盖下标 4','C',0,3);
INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
VALUES(@m5_q,'栈空，需先初始化','D',0,4);
INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
VALUES(@m5_q,@m5_kp);
SET @m5_fp=(SELECT SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
    ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
  FROM question q WHERE q.id=@m5_q);
INSERT INTO question_understanding_content(question_id,content_version,review_status,question_fingerprint,knowledge_point_id,solution_explanation,solution_content_source,solution_viz_title,solution_viz_spec_json,source_title,source_url,source_verification)
VALUES(@m5_q,1,'PENDING',@m5_fp,@m5_kp,'有效下标为 0 至 4。top=4 表示五个槽均已占用；Push 应报告满栈，不修改栈与 top。','AI_GENERATED',NULL,NULL,'原创 · st-08',NULL,'PENDING');
SET @m5_content=LAST_INSERT_ID();
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'越过数组最后一个下标','容量 5 的有效下标是 0..4；top=4 时没有下标 5 可写。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='A' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把入栈当覆盖栈顶','Push 应新增元素而非覆盖当前栈顶，下标 4 已被占用。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='C' AND deleted=0;
INSERT INTO question_understanding_misconception(content_id,option_id,title,explanation,content_source,viz_title,viz_spec_json)
SELECT @m5_content,id,'把满栈与空栈条件混淆','该表示下空栈 top=-1；top=4 是容量 5 的满栈。','AI_GENERATED',NULL,NULL FROM question_option WHERE question_id=@m5_q AND option_label='D' AND deleted=0;
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,1,'top 指向当前已占用的栈顶，不是下一空位。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,2,'写出容量 5 的全部有效下标，再看 top 是否已到末端。','AI_GENERATED','PENDING');
INSERT INTO question_understanding_hint(content_id,hint_level,hint_text,content_source,review_status)
VALUES(@m5_content,3,'例如容量 2 时，top=1 已占用下标 0、1，再 Push 就没有空槽。','AI_GENERATED','PENDING');
INSERT INTO question_ai_asset(question_id,asset_type,content,deleted)
VALUES(@m5_q,'VARIANT','M5 待审核草稿变式',0);
SET @m5_asset=LAST_INSERT_ID();
INSERT INTO ai_variant_question(asset_id,question_type,question_content,options_json,correct_answer,analysis,difficulty,review_status,changed_condition,viz_title,viz_spec_json)
VALUES(@m5_asset,'SINGLE_CHOICE','同一顺序栈仍为容量 5，只把 Push 前的 top 改为 3。执行一次 Push 后 top 是多少？',CAST('[{"label":"A","content":"3"},{"label":"B","content":"4"},{"label":"C","content":"5"},{"label":"D","content":"Push 必须拒绝"}]' AS JSON),'B','下标 4 尚空，Push 可把新元素写入该槽，并令 top 从 3 增至 4。',3,'PENDING','操作前 top 由 4 改为 3，容量和表示不变。',NULL,NULL);
