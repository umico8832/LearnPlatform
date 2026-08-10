-- 第十三个 Web Tutor 切片：独立审查 ArrayDeque 的循环数组表示与随机访问，不延伸至按较近端搬移、resize 或复杂度。
INSERT INTO knowledge_point (
    name, description, course_id, parent_id, content_key, content_source, content_version,
    content_review_status, sort_order, deleted
)
SELECT
    'ArrayDeque 的循环数组表示与访问',
    'ArrayDeque 用逻辑起点 j 与元素数量 n 表示 List；逻辑下标 i 映射为 a[(j+i) mod capacity]，get/set 可直接访问。',
    course.id, parent.id, 'ods-arraydeque-representation', 'AISTU', 1, 'REVIEWED', 21, 0
FROM course
JOIN knowledge_point parent ON parent.course_id = course.id
    AND parent.content_key = '408-stacks-queues-arrays'
LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-arraydeque-representation'
WHERE course.content_key = 'cs408-data-structures'
  AND existing.id IS NULL;

INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-arraydeque-representation', 1, 'REVIEWED', 'ArrayDeque 的循环数组表示与访问',
    JSON_OBJECT(
        'summary', 'ArrayDeque 用逻辑起点 j 与元素数量 n 表示 List。逻辑下标 i 位于 a[(j+i) mod capacity]，因此 get/set 无需移动元素即可直接访问；逻辑序列可以在数组末端回绕。',
        'steps', JSON_ARRAY(
            '用 j 标记逻辑 List 的第 0 个元素所在的物理下标，用 n 记录元素数量',
            '对 0 ≤ i < n，逻辑元素 i 映射到 a[(j+i) mod capacity]',
            'get(i) 读取该槽位，set(i, x) 写回该槽位；两者不需要搬移其他元素',
            '取模只处理物理下标回绕，逻辑 List 的顺序仍由 i = 0 到 n-1 定义'
        ),
        'prerequisite', JSON_OBJECT(
            'contentKey', 'ods-arrayqueue-representation',
            'title', 'ArrayQueue 的循环数组表示',
            'description', '先理解 j、n 与模运算如何把逻辑位置映射到循环数组槽位，再把同一映射用于 List 的任意下标访问。'
        ),
        'visualization', JSON_OBJECT(
            'kind', 'ARRAY_DEQUE_REPRESENTATION', 'version', 1, 'capacity', 8, 'headIndex', 6,
            'elements', JSON_ARRAY('A', 'B', 'C', 'D', 'E'), 'accessIndex', 3
        )
    ),
    JSON_OBJECT(
        'id', 'arraydeque-representation-access-v1',
        'prompt', '容量为 8，j = 6，n = 5。执行 get(3) 时，应读取哪个物理槽位？',
        'options', JSON_ARRAY(
            JSON_OBJECT('id', 'INDEX_ONE', 'text', 'a[1]，因为 (6 + 3) mod 8 = 1'),
            JSON_OBJECT('id', 'SHIFT_THEN_READ', 'text', '先将所有元素搬到 a[0] 起始位置，再读取 a[3]')
        ),
        'correctOptionId', 'INDEX_ONE',
        'correctExplanation', '正确：get(3) 直接按 a[(j+3) mod capacity] 定位，(6+3) mod 8 = 1；回绕不需要搬移已有元素。',
        'incorrectExplanation', '不正确：ArrayDeque 的 get/set 直接利用循环下标映射访问。只有插入或删除才会讨论元素搬移，本切片不涉及该主题。'
    )
FROM knowledge_point point
LEFT JOIN tutor_content existing ON existing.content_key = 'ods-arraydeque-representation' AND existing.content_version = 1
WHERE point.content_key = 'ods-arraydeque-representation'
  AND existing.id IS NULL;
