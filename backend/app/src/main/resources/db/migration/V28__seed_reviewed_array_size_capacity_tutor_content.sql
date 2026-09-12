-- 第三个 Web Tutor 切片：独立审查 ArrayStack 的有效元素数与后备数组容量表示不变量。
INSERT INTO knowledge_point (
    name, description, course_id, parent_id, content_key, content_source, content_version,
    content_review_status, sort_order, deleted
)
SELECT
    '元素数量与数组容量',
    '区分有效元素数量 n 与后备数组容量 length(a)：逻辑元素只在 a[0] 到 a[n-1] 中，始终满足 0 ≤ n ≤ length(a)。',
    course.id,
    parent.id,
    'ods-array-size-capacity',
    'AISTU',
    1,
    'REVIEWED',
    9,
    0
FROM course
JOIN knowledge_point parent ON parent.course_id = course.id
    AND parent.content_key = '408-stacks-queues-arrays'
LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-array-size-capacity'
WHERE course.content_key = 'cs408-data-structures'
  AND existing.id IS NULL;

INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-array-size-capacity', 1, 'REVIEWED', '元素数量与数组容量',
    JSON_OBJECT(
        'summary', 'n 只统计逻辑序列中的有效元素；length(a) 是后备数组已分配的全部槽位。空闲槽位可供后续操作使用，但不属于逻辑序列。',
        'steps', JSON_ARRAY(
            '用 n 表示当前有效元素数量，而不是数组槽位总数',
            '逻辑元素连续存放在 a[0] 到 a[n-1]',
            '用 length(a) 表示后备数组容量，其中可能包含空闲槽位',
            '保持不变量 0 ≤ n ≤ length(a)'
        )
    ),
    JSON_OBJECT(
        'id', 'array-size-capacity-invariant-v1',
        'prompt', '后备数组 a 的 length(a) 为 5，当前 n 为 3。下列哪项正确？',
        'options', JSON_ARRAY(
            JSON_OBJECT('id', 'THREE_LOGICAL_ELEMENTS', 'text', '逻辑序列有 3 个元素，a[3] 与 a[4] 是空闲槽位'),
            JSON_OBJECT('id', 'FIVE_LOGICAL_ELEMENTS', 'text', '逻辑序列有 5 个元素，因为数组有 5 个槽位')
        ),
        'correctOptionId', 'THREE_LOGICAL_ELEMENTS',
        'correctExplanation', '正确：n = 3 只让 a[0] 到 a[2] 成为逻辑元素；容量为 5 不会把空闲槽位计入元素数量。',
        'incorrectExplanation', '不正确：length(a) 表示已分配槽位总数，不等于当前逻辑元素数量；此处 n 才是 3。'
    )
FROM knowledge_point point
LEFT JOIN tutor_content existing ON existing.content_key = 'ods-array-size-capacity' AND existing.content_version = 1
WHERE point.content_key = 'ods-array-size-capacity'
  AND existing.id IS NULL;

UPDATE tutor_content
SET lesson_json = JSON_SET(
    lesson_json,
    '$.prerequisite.description', '先区分有效元素数量 n 与后备数组容量：只有 n 个槽位存放逻辑元素，且必须满足 n 不超过容量。可从课程目录进入该前置教学。'
)
WHERE content_key = 'ods-arraystack-insertion'
  AND content_version = 1
  AND review_status = 'REVIEWED';

UPDATE tutor_content
SET lesson_json = JSON_SET(
    lesson_json,
    '$.prerequisite.description', '先区分有效元素数量 n 与后备数组容量：删除后只保留 a[0] 到 a[n-1] 的逻辑元素。可从课程目录进入该前置教学。'
)
WHERE content_key = 'ods-arraystack-removal'
  AND content_version = 1
  AND review_status = 'REVIEWED';
