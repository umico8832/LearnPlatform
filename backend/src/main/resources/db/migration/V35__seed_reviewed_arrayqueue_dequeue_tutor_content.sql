-- 第十个 Web Tutor 切片：独立审查非空 ArrayQueue 的出队，只说明读出队首、j 循环前移与 n 减少，不延伸至 resize 或复杂度。
INSERT INTO knowledge_point (
    name, description, course_id, parent_id, content_key, content_source, content_version,
    content_review_status, sort_order, deleted
)
SELECT
    'ArrayQueue 的出队',
    '保存队首 a[j]，将 j 更新为 (j+1) mod capacity，再令 n 减一；其余队列元素无需搬移。',
    course.id, parent.id, 'ods-arrayqueue-dequeue', 'AISTU', 1, 'REVIEWED', 18, 0
FROM course
JOIN knowledge_point parent ON parent.course_id = course.id
    AND parent.content_key = '408-stacks-queues-arrays'
LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-arrayqueue-dequeue'
WHERE course.content_key = 'cs408-data-structures'
  AND existing.id IS NULL;

INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-arrayqueue-dequeue', 1, 'REVIEWED', 'ArrayQueue 的出队',
    JSON_OBJECT(
        'summary', '对于非空的 ArrayQueue，先保存队首 a[j]，再令 j = (j+1) mod capacity、n 减一；下一个已有元素自然成为队首，无需搬移。',
        'steps', JSON_ARRAY(
            '先确认队列非空；本切片只讨论不触发 resize 的出队',
            '保存当前队首元素 a[j]，它是本次 remove 的返回值',
            '将 j 更新为 (j+1) mod capacity，使下一个逻辑元素成为新队首',
            '将 n 减一；其余元素保持原物理位置和 FIFO 相对顺序'
        ),
        'visualization', JSON_OBJECT(
            'kind', 'ARRAY_QUEUE_DEQUEUE', 'version', 1, 'capacity', 8, 'headIndex', 6,
            'elements', JSON_ARRAY('A', 'B', 'C', 'D', 'E')
        )
    ),
    JSON_OBJECT(
        'id', 'arrayqueue-dequeue-advance-head-v1',
        'prompt', '容量为 8，j = 6，n = 5。非空队列出队并保存 a[6] 后，应如何更新队首状态？',
        'options', JSON_ARRAY(
            JSON_OBJECT('id', 'ADVANCE_HEAD', 'text', '令 j = 7、n = 4；下一个元素自然成为队首'),
            JSON_OBJECT('id', 'SHIFT_ELEMENTS', 'text', '将其余元素全部左移到 a[0] 起始位置，再令 n = 4')
        ),
        'correctOptionId', 'ADVANCE_HEAD',
        'correctExplanation', '正确：保存 a[j] 后，将 j 更新为 (j+1) mod capacity 并将 n 减一即可；循环数组不需要为出队搬移其余元素。',
        'incorrectExplanation', '不正确：ArrayQueue 使用 j 标记队首。出队后前移 j 即可使下一个逻辑元素成为新队首；本切片不涉及 resize。'
    )
FROM knowledge_point point
LEFT JOIN tutor_content existing ON existing.content_key = 'ods-arrayqueue-dequeue' AND existing.content_version = 1
WHERE point.content_key = 'ods-arrayqueue-dequeue'
  AND existing.id IS NULL;
