-- DualArrayDeque 模块：将按位更新映射到对应内部栈，并在更新后检查平衡。
INSERT INTO knowledge_point (name, description, course_id, parent_id, content_key, content_source, content_version, content_review_status, sort_order, deleted)
SELECT 'DualArrayDeque 的按位更新', '按 i 与 front.size() 的关系选择内部栈：前半段使用反向下标，后半段使用偏移下标；更新后调用 balance。', course.id, parent.id, 'ods-dualarraydeque-end-operations', 'AISTU', 1, 'REVIEWED', 25, 0
FROM course JOIN knowledge_point parent ON parent.course_id = course.id AND parent.content_key = '408-stacks-queues-arrays' LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-dualarraydeque-end-operations'
WHERE course.content_key = 'cs408-data-structures' AND existing.id IS NULL;
INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-dualarraydeque-end-operations', 1, 'REVIEWED', 'DualArrayDeque 的按位更新',
JSON_OBJECT('summary', 'DualArrayDeque 根据 i 是否小于 front.size() 决定把 add/remove 交给哪个内部栈。前半段使用反向下标，后半段使用减去 front.size() 的局部下标；操作后还要调用 balance。', 'steps', JSON_ARRAY('比较 i 与 front.size()，判断位置属于逻辑前缀还是后缀', '前缀位置换算为 front.size()-i（插入）或 front.size()-i-1（删除/读取）', '后缀位置换算为 i-front.size()，交给 back', '内部更新完成后调用 balance 检查是否三倍失衡'), 'prerequisite', JSON_OBJECT('contentKey', 'ods-dualarraydeque-representation', 'title', 'DualArrayDeque 的双栈表示', 'description', '先掌握 front 的逆序布局与两侧下标映射。'), 'nextStep', JSON_OBJECT('contentKey', 'ods-dualarraydeque-balance', 'title', 'DualArrayDeque 的再平衡', 'description', '继续理解更新后何时需要重建两栈。')),
JSON_OBJECT('id', 'dualarraydeque-operation-routing-v1', 'prompt', '执行按位更新时，i < front.size() 的位置应交给哪个内部栈处理？', 'options', JSON_ARRAY(JSON_OBJECT('id', 'ROUTE_FRONT', 'text', '交给 front，并使用反向下标'), JSON_OBJECT('id', 'ROUTE_BACK', 'text', '交给 back，并保持全局下标不变')), 'correctOptionId', 'ROUTE_FRONT', 'correctExplanation', '正确：逻辑前缀由 front 逆序保存，位置在前缀内时必须先换算为 front 的反向局部下标。', 'incorrectExplanation', '不正确：back 只保存逻辑后缀；直接带着全局下标操作会访问错误位置。')
FROM knowledge_point point LEFT JOIN tutor_content existing ON existing.content_key = 'ods-dualarraydeque-end-operations' AND existing.content_version = 1
WHERE point.content_key = 'ods-dualarraydeque-end-operations' AND existing.id IS NULL;
