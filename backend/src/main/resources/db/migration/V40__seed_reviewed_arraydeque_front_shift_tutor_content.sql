-- 第十四个 Web Tutor 切片：独立审查 ArrayDeque 在插入点靠近逻辑前端时的前缀搬移，不延伸至尾端分支、删除、resize 或复杂度。
INSERT INTO knowledge_point (name, description, course_id, parent_id, content_key, content_source, content_version, content_review_status, sort_order, deleted)
SELECT 'ArrayDeque 向较近端搬移', '当插入位置 i 靠近逻辑前端时，ArrayDeque 将 j 向左回绕一格，仅搬移前缀 a[0..i-1]，再写入新元素。', course.id, parent.id, 'ods-arraydeque-nearest-end-shifting', 'AISTU', 1, 'REVIEWED', 22, 0
FROM course JOIN knowledge_point parent ON parent.course_id = course.id AND parent.content_key = '408-stacks-queues-arrays'
LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-arraydeque-nearest-end-shifting'
WHERE course.content_key = 'cs408-data-structures' AND existing.id IS NULL;

INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-arraydeque-nearest-end-shifting', 1, 'REVIEWED', 'ArrayDeque 向较近端搬移',
    JSON_OBJECT(
        'summary', '插入位置 i 靠近逻辑前端时，ArrayDeque 先将 j 向左回绕一格，再仅搬移逻辑前缀 a[0..i-1]，最后在 a[i] 写入新元素；不必固定搬移后缀。',
        'steps', JSON_ARRAY('比较 i 与 n / 2；本切片只讨论 i < n / 2 的前端分支', '将 j 更新为 (j-1) mod capacity，为新的逻辑前端腾出槽位', '将原逻辑前缀 a[0..i-1] 向新的前端方向搬移一格', '在新的逻辑位置 i 写入元素，再将 n 加一'),
        'prerequisite', JSON_OBJECT('contentKey', 'ods-arraydeque-representation', 'title', 'ArrayDeque 的循环数组表示与访问', 'description', '先理解逻辑下标如何从 j 映射到循环数组物理槽位，再判断前缀搬移和 j 回绕。'),
        'visualization', JSON_OBJECT('kind', 'ARRAY_DEQUE_FRONT_SHIFT_INSERT', 'version', 1, 'capacity', 8, 'headIndex', 2, 'elements', JSON_ARRAY('A', 'B', 'C', 'D', 'E'), 'insertIndex', 1, 'insertValue', 'X')
    ),
    JSON_OBJECT(
        'id', 'arraydeque-front-shift-insert-v1',
        'prompt', 'ArrayDeque 有 n = 5 个元素，准备在 i = 1 插入新元素。忽略 resize，应选择哪种搬移？',
        'options', JSON_ARRAY(JSON_OBJECT('id', 'SHIFT_FRONT', 'text', '将 j 向左回绕一格，只搬移前缀中的 1 个元素'), JSON_OBJECT('id', 'SHIFT_ALL_SUFFIX', 'text', '固定搬移从 i 到末尾的全部 4 个元素')),
        'correctOptionId', 'SHIFT_FRONT',
        'correctExplanation', '正确：i = 1 小于 n / 2，插入点更靠近逻辑前端。将 j 向左回绕并只搬移前缀，可避免不必要地搬移后缀。',
        'incorrectExplanation', '不正确：ArrayDeque 根据离哪一端更近选择搬移方向。此处固定搬移较长后缀不是较少搬移的分支。'
    )
FROM knowledge_point point LEFT JOIN tutor_content existing ON existing.content_key = 'ods-arraydeque-nearest-end-shifting' AND existing.content_version = 1
WHERE point.content_key = 'ods-arraydeque-nearest-end-shifting' AND existing.id IS NULL;

UPDATE tutor_content
SET lesson_json = JSON_SET(lesson_json, '$.nextStep', JSON_OBJECT('contentKey', 'ods-arraydeque-nearest-end-shifting', 'title', 'ArrayDeque 向较近端搬移', 'description', '继续学习插入或删除时为什么选择距离操作位置较近的一端搬移。'))
WHERE content_key = 'ods-arraydeque-representation' AND content_version = 1 AND review_status = 'REVIEWED';
