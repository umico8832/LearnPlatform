-- 第二个 Web Tutor 切片：在首个按位插入之后迁入同一原始来源中可独立验证的按位删除知识。
-- 历史 V24 不变；为既有内容补充服务端应返回的已审查解释。
UPDATE tutor_content
SET check_json = JSON_SET(
    check_json,
    '$.correctExplanation', '正确：从右向左先腾出后面的槽位，避免覆盖尚未搬移的元素。',
    '$.incorrectExplanation', '不正确：从左向右会先覆盖 a[i+1] 等尚未搬走的元素。'
)
WHERE content_key = 'ods-arraystack-insertion'
  AND content_version = 1
  AND review_status = 'REVIEWED';

INSERT INTO knowledge_point (
    name, description, course_id, parent_id, content_key, content_source, content_version,
    content_review_status, sort_order, deleted
)
SELECT
    'ArrayStack 按位删除',
    '保存删除值、将后缀从左向右左移填补空位、更新有效元素数量；用于解释连续存储的删除成本。',
    course.id,
    parent.id,
    'ods-arraystack-removal',
    'AISTU',
    1,
    'REVIEWED',
    11,
    0
FROM course
JOIN knowledge_point parent ON parent.course_id = course.id
    AND parent.content_key = '408-stacks-queues-arrays'
LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-arraystack-removal'
WHERE course.content_key = 'cs408-data-structures'
  AND existing.id IS NULL;

INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-arraystack-removal', 1, 'REVIEWED', 'ArrayStack 的按位删除',
    JSON_OBJECT(
        'summary', '先保存 a[i]，再把 i+1 到 n-1 的元素从左向右左移一格填补空位，最后令 n 减少。',
        'prerequisite', JSON_OBJECT(
            'contentKey', 'ods-array-size-capacity',
            'title', '元素数量与数组容量',
            'description', '先区分有效元素数量 n 与后备数组容量：删除后只保留 a[0] 到 a[n-1] 的逻辑元素。该独立知识尚未迁入为可打开的 Web 教学内容。'
        ),
        'nextStep', JSON_OBJECT(
            'contentKey', 'ods-arraystack-performance',
            'title', 'ArrayStack 的操作复杂度',
            'description', '理解左移后，再分析删除位置 i 与后缀长度 n - i 如何决定一次按位删除的搬移成本。该独立知识尚未迁入为可打开的 Web 教学内容。'
        ),
        'steps', JSON_ARRAY(
            '保存待删除的 a[i]',
            '从 a[i+1] 到 a[n-1] 依次左移一格',
            '更新 n = n - 1；尾部槽位不再属于逻辑序列',
            '必要时再按实现约定检查是否缩容'
        )
    ),
    JSON_OBJECT(
        'id', 'arraystack-removal-shift-direction-v1',
        'prompt', '删除 a[1] 后，后缀应按什么方向搬移才能不丢失尚未读取的元素？',
        'options', JSON_ARRAY(
            JSON_OBJECT('id', 'LEFT_TO_RIGHT', 'text', '从左向右'),
            JSON_OBJECT('id', 'RIGHT_TO_LEFT', 'text', '从右向左')
        ),
        'correctOptionId', 'LEFT_TO_RIGHT',
        'correctExplanation', '正确：删除后从左向右搬移后缀，填补空位。',
        'incorrectExplanation', '不正确：从右向左会先覆盖尚未读取的后继元素。'
    )
FROM knowledge_point point
LEFT JOIN tutor_content existing ON existing.content_key = 'ods-arraystack-removal' AND existing.content_version = 1
WHERE point.content_key = 'ods-arraystack-removal'
  AND existing.id IS NULL;
