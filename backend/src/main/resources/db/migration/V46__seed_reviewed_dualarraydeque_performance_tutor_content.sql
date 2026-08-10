-- DualArrayDeque 模块：总结按位置更新、内部 resize 与 balance 的渐近性能。
INSERT INTO knowledge_point (name, description, course_id, parent_id, content_key, content_source, content_version, content_review_status, sort_order, deleted)
SELECT 'DualArrayDeque 的操作复杂度', 'get/set 为 O(1)，按位 add/remove 为 O(1+min(i,n-i))；内部 resize 与 balance 从空结构开始的总成本都为 O(m)。', course.id, parent.id, 'ods-dualarraydeque-performance', 'AISTU', 1, 'REVIEWED', 28, 0
FROM course JOIN knowledge_point parent ON parent.course_id = course.id AND parent.content_key = '408-stacks-queues-arrays' LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-dualarraydeque-performance'
WHERE course.content_key = 'cs408-data-structures' AND existing.id IS NULL;
INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-dualarraydeque-performance', 1, 'REVIEWED', 'DualArrayDeque 的操作复杂度',
JSON_OBJECT('summary', 'DualArrayDeque 的 get/set 为 O(1)。忽略内部 resize 与 balance 时，add(i,x)/remove(i) 为 O(1+min(i,n-i))；从空结构开始的 m 次更新中，两类重建的总成本均为 O(m)，渐近性能与 ArrayDeque 相同。', 'steps', JSON_ARRAY('两个内部 ArrayStack 都支持 O(1) 的局部随机访问', '前后两侧分别靠近各自栈顶，按位更新只移动距较近端的一段', '内部数组 resize 与双栈 balance 都可能产生一次线性复制', '从空结构开始，多次 resize 与 balance 的总成本都可线性摊还'), 'prerequisite', JSON_OBJECT('contentKey', 'ods-dualarraydeque-amortized-balance', 'title', 'DualArrayDeque 再平衡的摊还成本', 'description', '先确认 balance 的线性复制可以被多次更新分摊。')),
JSON_OBJECT('id', 'dualarraydeque-performance-v1', 'prompt', 'DualArrayDeque 的 add(i,x) 或 remove(i)（忽略 resize 与 balance）的时间界是什么？', 'options', JSON_ARRAY(JSON_OBJECT('id', 'NEAREST_END', 'text', 'O(1 + min(i, n-i))，只需处理距较近端的一段'), JSON_OBJECT('id', 'ALWAYS_LINEAR', 'text', '始终为 O(n)，因为每次都要重建两栈')), 'correctOptionId', 'NEAREST_END', 'correctExplanation', '正确：位置会被路由到靠近的内部栈，移动量受距逻辑两端的较小值限制。', 'incorrectExplanation', '不正确：再平衡不是每次更新都会发生；忽略它时的按位更新界取决于距较近端的距离。')
FROM knowledge_point point LEFT JOIN tutor_content existing ON existing.content_key = 'ods-dualarraydeque-performance' AND existing.content_version = 1
WHERE point.content_key = 'ods-dualarraydeque-performance' AND existing.id IS NULL;
