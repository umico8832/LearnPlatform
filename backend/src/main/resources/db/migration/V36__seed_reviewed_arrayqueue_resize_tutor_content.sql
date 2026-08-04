-- 第十一个 Web Tutor 切片：独立审查跨界 ArrayQueue 的 resize，只说明按逻辑 FIFO 顺序复制到新数组并重置 j，不延伸至触发条件或摊还复杂度。
INSERT INTO knowledge_point (
    name, description, course_id, parent_id, content_key, content_source, content_version,
    content_review_status, sort_order, deleted
)
SELECT
    'ArrayQueue 调整容量时的线性化复制',
    'resize 按逻辑 FIFO 顺序将元素复制到新数组 b[0..n-1]，并将 j 重置为 0，使跨界队列连续排列。',
    course.id, parent.id, 'ods-arrayqueue-resize', 'AISTU', 1, 'REVIEWED', 19, 0
FROM course
JOIN knowledge_point parent ON parent.course_id = course.id
    AND parent.content_key = '408-stacks-queues-arrays'
LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-arrayqueue-resize'
WHERE course.content_key = 'cs408-data-structures'
  AND existing.id IS NULL;

INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-arrayqueue-resize', 1, 'REVIEWED', 'ArrayQueue 调整容量时的线性化复制',
    JSON_OBJECT(
        'summary', 'ArrayQueue 调整容量时，从旧队首 j 开始按 FIFO 逻辑顺序复制到新数组 b[0..n-1]，再令 a = b、j = 0；旧数组中的回绕被线性化，但队列顺序不变。',
        'steps', JSON_ARRAY(
            '识别旧数组中从 j 开始、可能跨越数组末端的逻辑 FIFO 顺序',
            '分配容量为 max(1, 2n) 的新数组 b',
            '对 k = 0 到 n - 1，复制 b[k] = a[(j+k) mod oldCapacity]',
            '令 a = b、j = 0；队列在新数组中从 b[0] 连续排列'
        ),
        'visualization', JSON_OBJECT(
            'kind', 'ARRAY_QUEUE_RESIZE', 'version', 1, 'previousCapacity', 8, 'headIndex', 6,
            'elements', JSON_ARRAY('A', 'B', 'C', 'D', 'E')
        )
    ),
    JSON_OBJECT(
        'id', 'arrayqueue-resize-linearize-v1',
        'prompt', '容量为 8 的 ArrayQueue 中，j = 6，逻辑 FIFO 顺序为 A、B、C、D、E。resize 到新数组后，哪种状态保持了正确顺序？',
        'options', JSON_ARRAY(
            JSON_OBJECT('id', 'COPY_LOGICAL_ORDER', 'text', '将 A、B、C、D、E 依次复制到 b[0] 至 b[4]，再令 j = 0'),
            JSON_OBJECT('id', 'COPY_PHYSICAL_ORDER', 'text', '按旧数组物理下标 0 至 7 的顺序复制，并保留 j = 6')
        ),
        'correctOptionId', 'COPY_LOGICAL_ORDER',
        'correctExplanation', '正确：resize 从旧队首开始按逻辑 FIFO 顺序复制到 b[0..n-1]，随后 j 重置为 0；旧数组是否回绕不改变队列顺序。',
        'incorrectExplanation', '不正确：物理下标顺序不能代表跨界队列的 FIFO 顺序。应按 a[(j+k) mod oldCapacity] 复制到连续的新数组槽位，并令 j = 0。'
    )
FROM knowledge_point point
LEFT JOIN tutor_content existing ON existing.content_key = 'ods-arrayqueue-resize' AND existing.content_version = 1
WHERE point.content_key = 'ods-arrayqueue-resize'
  AND existing.id IS NULL;
