CREATE TABLE community_category (
 id VARCHAR(80) PRIMARY KEY, parent_id VARCHAR(80) NULL, kind VARCHAR(12) NOT NULL,
 name VARCHAR(120) NOT NULL, description VARCHAR(500) NOT NULL DEFAULT '',
 UNIQUE KEY uk_category_name (kind, name), KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE community_post (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 title VARCHAR(120) NOT NULL, body TEXT NOT NULL, content_type VARCHAR(30) NOT NULL,
 subject_id VARCHAR(80) NOT NULL, school_id VARCHAR(80) NULL,
 course_id BIGINT NULL, knowledge_point_id BIGINT NULL, concept_name VARCHAR(80) NOT NULL DEFAULT '',
 source_note VARCHAR(500) NOT NULL DEFAULT '', status VARCHAR(12) NOT NULL,
 review_note VARCHAR(500) NOT NULL DEFAULT '', reviewed_by BIGINT NULL, reviewed_at DATETIME NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
 KEY idx_feed (deleted, status, id), KEY idx_owner (user_id, id), KEY idx_subject (subject_id, id),
 KEY idx_course (course_id), KEY idx_knowledge (knowledge_point_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE community_attachment (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, post_id BIGINT NOT NULL, name VARCHAR(180) NOT NULL,
 size_bytes BIGINT NOT NULL, content LONGBLOB NOT NULL, KEY idx_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE community_comment (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, post_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 parent_id BIGINT NULL, body VARCHAR(2000) NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_thread (post_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE community_like (
 post_id BIGINT NOT NULL, comment_id BIGINT NOT NULL DEFAULT 0, user_id BIGINT NOT NULL,
 PRIMARY KEY (post_id, comment_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE community_review (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, post_id BIGINT NOT NULL, reviewer_id BIGINT NOT NULL,
 decision VARCHAR(12) NOT NULL, note VARCHAR(500) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_review (post_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE community_question_link (
 post_id BIGINT NOT NULL, submission_id BIGINT NOT NULL, request_key VARCHAR(64) NOT NULL,
 UNIQUE KEY uk_community_request (post_id,request_key), PRIMARY KEY (post_id, submission_id),
 UNIQUE KEY uk_submission (submission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO community_category (id,kind,name,description) VALUES ('computer-science-408','EXAM','408 计算机学科专业基础','考试涵盖数据结构、计算机组成原理、操作系统和计算机网络。当前可从数据结构开始，按考纲学习核心概念与互动课件。');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('data-structures','computer-science-408','SUBJECT','数据结构','线性结构、树、图、查找与排序');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('computer-organization','computer-science-408','SUBJECT','计算机组成原理','数据表示、存储系统与处理器');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('operating-systems','computer-science-408','SUBJECT','操作系统','进程、内存、文件与 I/O');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('computer-network','computer-science-408','SUBJECT','计算机网络','网络体系、协议与可靠传输');
INSERT INTO community_category (id,kind,name,description) VALUES ('national-gaokao','EXAM','普通高考','汇集语文、数学、英语与物理、化学、生物、政治、历史、地理科目课程，围绕核心考点梳理知识框架。');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('gaokao-mathematics','national-gaokao','SUBJECT','高考数学','函数、几何、概率统计与综合应用');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('gaokao-chinese','national-gaokao','SUBJECT','高考语文','阅读、语言运用与写作');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('gaokao-english','national-gaokao','SUBJECT','高考英语','语言知识与综合运用');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('gaokao-physics','national-gaokao','SUBJECT','高考物理','力学、电磁学与实验能力');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('gaokao-chemistry','national-gaokao','SUBJECT','高考化学','物质结构、反应原理与实验');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('gaokao-biology','national-gaokao','SUBJECT','高考生物学','生命过程、遗传与生态');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('gaokao-politics','national-gaokao','SUBJECT','思想政治','核心概念、材料分析与表达');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('gaokao-history','national-gaokao','SUBJECT','高考历史','时空线索、史料与历史解释');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('gaokao-geography','national-gaokao','SUBJECT','高考地理','自然过程、人文空间与图表');
INSERT INTO community_category (id,kind,name,description) VALUES ('postgraduate-public','EXAM','考研公共课','汇集思想政治理论、英语（一）（二）与数学（一）（二）（三）课程，按卷种组织复习讲解与练习。');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('postgraduate-politics','postgraduate-public','SUBJECT','思想政治理论','理论框架、时政材料与分析');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('postgraduate-english-one','postgraduate-public','SUBJECT','英语（一）','阅读、翻译与写作');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('postgraduate-english-two','postgraduate-public','SUBJECT','英语（二）','阅读、翻译与写作');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('postgraduate-math-one','postgraduate-public','SUBJECT','数学（一）','高等数学、线代与概率统计');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('postgraduate-math-two','postgraduate-public','SUBJECT','数学（二）','高等数学与线性代数');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('postgraduate-math-three','postgraduate-public','SUBJECT','数学（三）','微积分、线代与概率统计');
INSERT INTO community_category (id,kind,name,description) VALUES ('college-english-test','EXAM','大学英语四六级','设置四级、六级两个级别课程，围绕听力、阅读、翻译与写作展开系统训练。');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('cet-four','college-english-test','SUBJECT','英语四级 CET-4','基础阶段综合英语能力');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('cet-six','college-english-test','SUBJECT','英语六级 CET-6','进阶阶段综合英语能力');
INSERT INTO community_category (id,kind,name,description) VALUES ('national-computer-rank','EXAM','全国计算机等级考试','设置一级到四级课程，从计算机基础与办公应用逐步进阶到程序设计与工程师级综合能力。');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('ncre-level-one','national-computer-rank','SUBJECT','一级','计算机基础与办公应用');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('ncre-level-two','national-computer-rank','SUBJECT','二级','程序设计与办公高级应用');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('ncre-level-three','national-computer-rank','SUBJECT','三级','网络、数据库等技术方向');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('ncre-level-four','national-computer-rank','SUBJECT','四级','工程师级综合能力');
INSERT INTO community_category (id,kind,name,description) VALUES ('teacher-qualification','EXAM','中小学教师资格考试','按幼儿园、小学、初中、高中四个学段设置课程，覆盖综合素质、教育知识与学科能力。');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('teacher-kindergarten','teacher-qualification','SUBJECT','幼儿园','综合素质与保教知识能力');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('teacher-primary','teacher-qualification','SUBJECT','小学','综合素质与教育教学能力');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('teacher-junior-secondary','teacher-qualification','SUBJECT','初级中学','综合素质、教育知识与学科能力');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('teacher-senior-secondary','teacher-qualification','SUBJECT','高级中学','综合素质、教育知识与学科能力');
INSERT INTO community_category (id,kind,name,description) VALUES ('adult-gaokao','EXAM','成人高考','按高起专、高起本、专升本三个报考层次设置课程，梳理公共科目与专业基础内容。');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('adult-gaokao-junior','adult-gaokao','SUBJECT','高中起点升专科','按层次整理公共科目与复习经验');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('adult-gaokao-bachelor','adult-gaokao','SUBJECT','高中起点升本科','按层次整理公共与专业基础内容');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('adult-gaokao-upgrade','adult-gaokao','SUBJECT','专科起点升本科','按专业类别整理复习重点');
INSERT INTO community_category (id,kind,name,description) VALUES ('self-taught-exam','EXAM','高等教育自学考试','涵盖公共基础课、专业核心课、选修课与实践考核四类课程，支持按专业计划安排复习。');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('self-taught-public','self-taught-exam','SUBJECT','公共基础课','跨专业公共课程复习经验');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('self-taught-core','self-taught-exam','SUBJECT','专业核心课','按专业计划沉淀核心课程资料');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('self-taught-elective','self-taught-exam','SUBJECT','选修课','选课与阶段复习方法');
INSERT INTO community_category (id,parent_id,kind,name,description) VALUES ('self-taught-practice','self-taught-exam','SUBJECT','实践考核','实践环节准备与经验');
