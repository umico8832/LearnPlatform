-- 第四个 Web Tutor 切片：独立审查 ArrayStack 容量调整，不迁入摊还复杂度结论。
INSERT INTO knowledge_point (
    name, description, course_id, parent_id, content_key, content_source, content_version,
    content_review_status, sort_order, deleted
)
SELECT
    'ArrayStack 的容量调整',
    '分配长度 max(1, 2n) 的新数组，按原顺序复制 n 个有效元素，再切换后备数组；用于说明扩容和低占用缩容的共同机制。',
    course.id,
    parent.id,
    'ods-arraystack-resize',
    'AISTU',
    1,
    'REVIEWED',
    12,
    0
FROM course
JOIN knowledge_point parent ON parent.course_id = course.id
    AND parent.content_key = '408-stacks-queues-arrays'
LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-arraystack-resize'
WHERE course.content_key = 'cs408-data-structures'
  AND existing.id IS NULL;

INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-arraystack-resize', 1, 'REVIEWED', 'ArrayStack 的容量调整',
    JSON_OBJECT(
        'summary', 'resize 分配长度 max(1, 2n) 的新数组，将 n 个有效元素按原顺序复制后替换后备数组；一次调整复制 n 个元素，耗时 O(n)。',
        'prerequisite', JSON_OBJECT(
            'contentKey', 'ods-array-size-capacity',
            'title', '元素数量与数组容量',
            'description', '先区分有效元素数量 n 与后备数组容量：resize 只复制 a[0] 到 a[n-1] 的逻辑元素。可从课程目录进入该前置教学。'
        ),
        'nextStep', JSON_OBJECT(
            'contentKey', 'ods-arraystack-amortized-resize',
            'title', 'ArrayStack 调整容量的摊还成本',
            'description', '理解一次复制的 O(n) 成本后，再分析为什么一系列 add/remove 的全部 resize 成本仍可摊还为 O(m)。该独立知识尚未迁入为可打开的 Web 教学内容。'
        ),
        'steps', JSON_ARRAY(
            '以 n 个有效元素为准，计算新容量 max(1, 2n)',
            '分配新数组 b，旧数组的空闲槽位不需要复制',
            '按 a[0] 到 a[n-1] 的原顺序复制到 b[0] 到 b[n-1]',
            '令 a 指向 b；n 不变，单次 resize 复制 n 个元素，耗时 O(n)'
        ),
        'visualization', JSON_OBJECT(
            'kind', 'ARRAY_STACK_RESIZE',
            'version', 1,
            'previousCapacity', 3,
            'initialElements', JSON_ARRAY('A', 'B', 'C')
        )
    ),
    JSON_OBJECT(
        'id', 'arraystack-resize-target-capacity-v1',
        'prompt', '当前 n = 3。执行 resize 后，新数组 b 的长度应为多少？',
        'options', JSON_ARRAY(
            JSON_OBJECT('id', 'SIX', 'text', '6，因为新容量为 max(1, 2 × 3)'),
            JSON_OBJECT('id', 'THREE', 'text', '3，因为只需保存 3 个有效元素')
        ),
        'correctOptionId', 'SIX',
        'correctExplanation', '正确：resize 使用 max(1, 2n)，n = 3 时分配长度 6 的新数组；其中前 3 个槽位复制有效元素。',
        'incorrectExplanation', '不正确：新数组必须预留后续操作的空闲槽位；本算法在 n = 3 时分配 max(1, 2 × 3) = 6 个槽位。'
    )
FROM knowledge_point point
LEFT JOIN tutor_content existing ON existing.content_key = 'ods-arraystack-resize' AND existing.content_version = 1
WHERE point.content_key = 'ods-arraystack-resize'
  AND existing.id IS NULL;
