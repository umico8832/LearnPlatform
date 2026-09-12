-- 第八个 Web Tutor 切片：独立审查 ArrayQueue 的循环数组表示，只说明 j、n 与模运算映射，不延伸至入队、出队和 resize。
INSERT INTO knowledge_point (
    name, description, course_id, parent_id, content_key, content_source, content_version,
    content_review_status, sort_order, deleted
)
SELECT
    'ArrayQueue 的循环数组表示',
    '用队首物理下标 j 和元素数量 n 表示 FIFO 队列；逻辑位置 k 映射到 a[(j+k) mod capacity]，因此队首可越过数组末端回绕而无需搬移元素。',
    course.id,
    parent.id,
    'ods-arrayqueue-representation',
    'AISTU',
    1,
    'REVIEWED',
    16,
    0
FROM course
JOIN knowledge_point parent ON parent.course_id = course.id
    AND parent.content_key = '408-stacks-queues-arrays'
LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-arrayqueue-representation'
WHERE course.content_key = 'cs408-data-structures'
  AND existing.id IS NULL;

INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-arrayqueue-representation', 1, 'REVIEWED', 'ArrayQueue 的循环数组表示',
    JSON_OBJECT(
        'summary', 'ArrayQueue 用队首物理下标 j 和元素数量 n 表示 FIFO 队列：第 k 个逻辑元素位于 a[(j+k) mod capacity]。回绕改变的是物理下标，不改变队列顺序。',
        'steps', JSON_ARRAY(
            '用 j 标记当前队首的物理下标，用 n 记录队列中的逻辑元素数量',
            '将第 k 个逻辑元素映射为 a[(j+k) mod capacity]，其中 0 ≤ k < n',
            '当 j+k 越过数组末端时，取模结果回到较小下标，已有元素不需要物理旋转',
            '从 j 开始按 k = 0 到 n-1 读取，仍得到原来的 FIFO 逻辑顺序'
        ),
        'visualization', JSON_OBJECT(
            'kind', 'ARRAY_QUEUE_REPRESENTATION',
            'version', 1,
            'capacity', 8,
            'headIndex', 6,
            'elements', JSON_ARRAY('A', 'B', 'C', 'D', 'E')
        )
    ),
    JSON_OBJECT(
        'id', 'arrayqueue-representation-wraparound-v1',
        'prompt', '容量为 8，j = 6，n = 5。逻辑位置 k = 3 的元素应位于哪个物理槽位？',
        'options', JSON_ARRAY(
            JSON_OBJECT('id', 'INDEX_ONE', 'text', 'a[1]，因为 (6 + 3) mod 8 = 1'),
            JSON_OBJECT('id', 'INDEX_NINE', 'text', 'a[9]，因为 j + k = 9')
        ),
        'correctOptionId', 'INDEX_ONE',
        'correctExplanation', '正确：物理下标必须落在容量为 8 的数组范围内，(6 + 3) mod 8 = 1；回绕不改变该元素在 FIFO 队列中的逻辑位置。',
        'incorrectExplanation', '不正确：a[9] 超出长度为 8 的数组合法下标。循环数组使用模运算将 9 映射为 1。'
    )
FROM knowledge_point point
LEFT JOIN tutor_content existing ON existing.content_key = 'ods-arrayqueue-representation' AND existing.content_version = 1
WHERE point.content_key = 'ods-arrayqueue-representation'
  AND existing.id IS NULL;
