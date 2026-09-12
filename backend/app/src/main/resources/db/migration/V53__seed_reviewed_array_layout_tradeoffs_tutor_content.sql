-- 数组式线性表收束比较：只比较已审查实现的更新位置、空间浪费与内部布局。
INSERT INTO knowledge_point (name, description, course_id, parent_id, content_key, content_source, content_version, content_review_status, sort_order, deleted)
SELECT '数组式线性表的布局权衡', 'ArrayStack 直接但中间更新搬移后缀；双端结构优化近端更新；RootishArrayStack 以递增块降低额外空间但增加下标映射。', course.id, parent.id, 'ods-array-layout-tradeoffs', 'AISTU', 1, 'REVIEWED', 35, 0
FROM course JOIN knowledge_point parent ON parent.course_id=course.id AND parent.content_key='408-stacks-queues-arrays' LEFT JOIN knowledge_point existing ON existing.content_key='ods-array-layout-tradeoffs'
WHERE course.content_key='cs408-data-structures' AND existing.id IS NULL;
INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-array-layout-tradeoffs', 1, 'REVIEWED', '数组式线性表的布局权衡',
JSON_OBJECT('summary','ArrayStack 以单数组提供直接访问，但按位更新搬移后缀；ArrayDeque 与 DualArrayDeque 将更新量限制到距较近端的一侧；RootishArrayStack 用递增块将额外空间降到 O(sqrt(n))，代价是下标定位公式与多块目录。', 'steps', JSON_ARRAY('单数组布局直接，适合主要在尾端更新的序列','循环双端与双栈双端布局优先降低靠近两端的更新搬移','递增块布局保持 O(1) 随机访问并降低长期空闲槽位','相同渐近界不代表常数开销、内存布局或实际速度相同'), 'prerequisite', JSON_OBJECT('contentKey','ods-rootisharraystack-performance','title','RootishArrayStack 的时间与空间复杂度','description','先掌握递增块布局的时间空间特征。')),
JSON_OBJECT('id','array-layout-tradeoffs-v1','prompt','若主要目标是降低额外空闲空间的渐近界，同时仍保留 O(1) 随机访问，应优先考虑哪种布局？','options',JSON_ARRAY(JSON_OBJECT('id','SPACE_TRADEOFF','text','RootishArrayStack 的递增块布局，额外空间为 O(sqrt(n))'),JSON_OBJECT('id','SAME_MEMORY','text','ArrayDeque，因为所有数组式结构的空闲空间界都相同')),'correctOptionId','SPACE_TRADEOFF','correctExplanation','正确：RootishArrayStack 通过递增块与收缩规则把未用槽位和块目录限制在 O(sqrt(n))。','incorrectExplanation','不正确：ArrayDeque 的优势主要是两端附近更新；不同数组布局的额外空间界并不相同。')
FROM knowledge_point point LEFT JOIN tutor_content existing ON existing.content_key='ods-array-layout-tradeoffs' AND existing.content_version=1
WHERE point.content_key='ods-array-layout-tradeoffs' AND existing.id IS NULL;
