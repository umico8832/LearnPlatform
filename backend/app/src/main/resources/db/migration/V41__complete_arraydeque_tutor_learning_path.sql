-- 收口 ArrayDeque Web Tutor 模块：独立审查操作复杂度，并将表示、近端搬移和复杂度组成受限学习路径。
INSERT INTO knowledge_point (name, description, course_id, parent_id, content_key, content_source, content_version, content_review_status, sort_order, deleted)
SELECT 'ArrayDeque 的操作复杂度', 'ArrayDeque 的 get/set 为 O(1)；忽略 resize 时，按位 add/remove 为 O(1 + min(i, n-i))，从空结构开始的一串更新中 resize 总成本为 O(m)。', course.id, parent.id, 'ods-arraydeque-performance', 'AISTU', 1, 'REVIEWED', 23, 0
FROM course JOIN knowledge_point parent ON parent.course_id = course.id AND parent.content_key = '408-stacks-queues-arrays'
LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-arraydeque-performance'
WHERE course.content_key = 'cs408-data-structures' AND existing.id IS NULL;

INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-arraydeque-performance', 1, 'REVIEWED', 'ArrayDeque 的操作复杂度',
    JSON_OBJECT(
        'summary', 'ArrayDeque 的 get/set 为 O(1)。忽略 resize 时，add(i, x) 与 remove(i) 只搬移到较近端的元素，时间为 O(1 + min(i, n-i))；从空结构开始的 m 次更新中，全部 resize 总成本为 O(m)。',
        'steps', JSON_ARRAY('get/set 直接使用循环下标映射，因此为 O(1)', '插入或删除比较 i 与 n / 2，选择搬移较近端的前缀或后缀', '忽略 resize 时，搬移元素数不超过 min(i, n-i)，故更新为 O(1 + min(i, n-i))', '从空结构开始的 m 次更新中，全部 resize 总成本为 O(m)，不将单次 resize 错写成最坏 O(1)'),
        'prerequisite', JSON_OBJECT('contentKey', 'ods-arraydeque-nearest-end-shifting', 'title', 'ArrayDeque 向较近端搬移', 'description', '先理解更新为什么只搬移距操作位置较近的一端，再将搬移数量写为 min(i, n-i)。')
    ),
    JSON_OBJECT(
        'id', 'arraydeque-performance-nearest-end-v1',
        'prompt', '忽略 resize，ArrayDeque 在 n 个元素中执行 add(i, x) 或 remove(i) 的时间应如何表述？',
        'options', JSON_ARRAY(JSON_OBJECT('id', 'NEAREST_END', 'text', 'O(1 + min(i, n-i))，因为只搬移距位置 i 较近一端的元素'), JSON_OBJECT('id', 'ALWAYS_SUFFIX', 'text', 'O(n-i)，因为始终搬移位置 i 之后的后缀')),
        'correctOptionId', 'NEAREST_END',
        'correctExplanation', '正确：ArrayDeque 根据 i 与 n / 2 选择前缀或后缀分支，搬移数不超过 min(i, n-i)；公式中的常数项包含定位和写入。',
        'incorrectExplanation', '不正确：固定搬移后缀只是一种可能分支，并非 ArrayDeque 的策略；当 i 靠近逻辑前端时，搬移前缀更少。'
    )
FROM knowledge_point point LEFT JOIN tutor_content existing ON existing.content_key = 'ods-arraydeque-performance' AND existing.content_version = 1
WHERE point.content_key = 'ods-arraydeque-performance' AND existing.id IS NULL;

UPDATE tutor_content
SET lesson_json = JSON_SET(lesson_json, '$.nextStep', JSON_OBJECT('contentKey', 'ods-arraydeque-performance', 'title', 'ArrayDeque 的操作复杂度', 'description', '完成近端搬移后，继续将搬移数量与操作位置的关系总结为时间复杂度。'))
WHERE content_key = 'ods-arraydeque-nearest-end-shifting' AND content_version = 1 AND review_status = 'REVIEWED';
