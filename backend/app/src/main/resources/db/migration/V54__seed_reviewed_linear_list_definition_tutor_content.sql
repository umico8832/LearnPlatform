-- 线性表模块首项：List ADT 的有限序列语义与基本操作边界。
INSERT INTO knowledge_point (name, description, course_id, parent_id, content_key, content_source, content_version, content_review_status, sort_order, deleted)
SELECT '线性表的定义与基本操作', '线性表是有限同类型元素序列；除首尾外每个元素有唯一前驱和唯一后继，操作下标范围随查找、插入和删除语义变化。', course.id, parent.id, 'cs408-linear-list-definition-operations', 'AISTU', 1, 'REVIEWED', 1, 0
FROM course JOIN knowledge_point parent ON parent.course_id=course.id AND parent.content_key='408-linear-lists' LEFT JOIN knowledge_point existing ON existing.content_key='cs408-linear-list-definition-operations'
WHERE course.content_key='cs408-data-structures' AND existing.id IS NULL;
INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id,'cs408-linear-list-definition-operations',1,'REVIEWED','线性表的定义与基本操作',JSON_OBJECT('summary','线性表 L=(a1,…,an) 是同类型元素构成的有限序列。除首尾外，每个元素有唯一前驱和唯一后继；初始化、判空、按位或按值查找、插入、删除、求长度和遍历都以此逻辑顺序为基础。','steps',JSON_ARRAY('线性表强调逻辑先后关系，不预设具体物理存储方式','首元素没有前驱，尾元素没有后继，中间元素各有唯一相邻元素','按位查找、插入和删除的合法下标范围不同','后续顺序表和链表都是同一 List ADT 的不同实现')),
JSON_OBJECT('id','linear-list-definition-v1','prompt','长度为 n 的非空线性表中，一个既非首也非尾的元素具有怎样的相邻关系？','options',JSON_ARRAY(JSON_OBJECT('id','UNIQUE_NEIGHBORS','text','恰有一个前驱和一个后继'),JSON_OBJECT('id','MULTIPLE_PREDECESSORS','text','可以有多个前驱，只要元素值不同')),'correctOptionId','UNIQUE_NEIGHBORS','correctExplanation','正确：唯一前驱和唯一后继是线性表的逻辑特征。','incorrectExplanation','不正确：若存在多个前驱或后继，就不再是线性结构。')
FROM knowledge_point point LEFT JOIN tutor_content existing ON existing.content_key='cs408-linear-list-definition-operations' AND existing.content_version=1
WHERE point.content_key='cs408-linear-list-definition-operations' AND existing.id IS NULL;
