-- 第九个 Web Tutor 切片：独立审查容量充足时的 ArrayQueue 入队，只说明循环队尾写入与 n 增加，不延伸至 resize、出队和复杂度。
INSERT INTO knowledge_point (
    name, description, course_id, parent_id, content_key, content_source, content_version,
    content_review_status, sort_order, deleted
)
SELECT
    'ArrayQueue 的入队',
    '容量足够时，将新元素写入 a[(j+n) mod capacity]，再令 n 加一；已有队列元素无需搬移。',
    course.id, parent.id, 'ods-arrayqueue-enqueue', 'AISTU', 1, 'REVIEWED', 17, 0
FROM course
JOIN knowledge_point parent ON parent.course_id = course.id
    AND parent.content_key = '408-stacks-queues-arrays'
LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-arrayqueue-enqueue'
WHERE course.content_key = 'cs408-data-structures'
  AND existing.id IS NULL;

INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-arrayqueue-enqueue', 1, 'REVIEWED', 'ArrayQueue 的入队',
    JSON_OBJECT(
        'summary', '当后备数组仍有空闲槽位时，ArrayQueue 将新元素写入 a[(j+n) mod capacity]，随后把 n 加一；已有 FIFO 元素不需要搬移。',
        'steps', JSON_ARRAY(
            '先确认 n 小于 capacity；本切片只讨论容量足够、不需要 resize 的情形',
            '当前队尾之后的物理写入位置为 a[(j+n) mod capacity]',
            '把新元素写入该空闲槽位，已有队列元素保持原物理位置',
            '将 n 加一，新元素成为逻辑队尾'
        ),
        'visualization', JSON_OBJECT(
            'kind', 'ARRAY_QUEUE_ENQUEUE', 'version', 1, 'capacity', 8, 'headIndex', 6,
            'elements', JSON_ARRAY('A', 'B', 'C', 'D', 'E'), 'enqueueValue', 'F'
        )
    ),
    JSON_OBJECT(
        'id', 'arrayqueue-enqueue-tail-slot-v1',
        'prompt', '容量为 8，j = 6，n = 5，且数组还有空闲槽位。新元素应先写入哪个槽位？',
        'options', JSON_ARRAY(
            JSON_OBJECT('id', 'TAIL_SLOT', 'text', 'a[3]，因为 (6 + 5) mod 8 = 3'),
            JSON_OBJECT('id', 'MOVE_AND_APPEND', 'text', '先搬移所有已有元素到 a[0] 起始位置，再写入 a[5]')
        ),
        'correctOptionId', 'TAIL_SLOT',
        'correctExplanation', '正确：容量足够时，入队位置是 a[(j+n) mod capacity] = a[3]，直接写入后将 n 加一；已有元素不需要搬移。',
        'incorrectExplanation', '不正确：循环数组通过 j、n 与模运算定位队尾空闲槽位。容量足够时不需要为入队搬移已有元素。'
    )
FROM knowledge_point point
LEFT JOIN tutor_content existing ON existing.content_key = 'ods-arrayqueue-enqueue' AND existing.content_version = 1
WHERE point.content_key = 'ods-arrayqueue-enqueue'
  AND existing.id IS NULL;
