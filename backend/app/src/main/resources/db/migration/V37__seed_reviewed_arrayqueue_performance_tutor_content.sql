-- 第十二个 Web Tutor 切片：独立审查 ArrayQueue 更新的摊还复杂度，区分忽略 resize 的常数时间、累计 resize 线性总成本与单次最坏成本。
INSERT INTO knowledge_point (
    name, description, course_id, parent_id, content_key, content_source, content_version,
    content_review_status, sort_order, deleted
)
SELECT
    'ArrayQueue 的操作复杂度',
    '忽略 resize 时入队与出队均为 O(1)；从空队列开始的 m 次更新中全部 resize 总成本为 O(m)，因此更新为 O(1) 摊还时间，但单次 resize 仍可能为 O(n)。',
    course.id, parent.id, 'ods-arrayqueue-performance', 'AISTU', 1, 'REVIEWED', 20, 0
FROM course
JOIN knowledge_point parent ON parent.course_id = course.id
    AND parent.content_key = '408-stacks-queues-arrays'
LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-arrayqueue-performance'
WHERE course.content_key = 'cs408-data-structures'
  AND existing.id IS NULL;

INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-arrayqueue-performance', 1, 'REVIEWED', 'ArrayQueue 的操作复杂度',
    JSON_OBJECT(
        'summary', '忽略 resize 时，ArrayQueue 的入队与出队均为 O(1)；从空队列开始的 m 次更新中全部 resize 总成本为 O(m)，所以更新为 O(1) 摊还时间。单次实际 resize 仍可能复制 n 个元素。',
        'prerequisite', JSON_OBJECT(
            'contentKey', 'ods-arrayqueue-resize',
            'title', 'ArrayQueue 调整容量时的线性化复制',
            'description', '先理解 resize 如何按 FIFO 逻辑顺序复制并重置队首；该前置教学已可从课程目录进入。'
        ),
        'steps', JSON_ARRAY(
            '容量足够的入队只计算队尾槽位、写入元素并增加 n，因此忽略 resize 时为 O(1)',
            '非空出队只读取队首、循环前移 j 并减少 n，因此忽略 resize 时也为 O(1)',
            '一次实际 resize 需要按 FIFO 顺序复制 n 个元素，所以该单次操作可能为 O(n)',
            '从空队列开始的一串 m 次更新中，全部 resize 的总成本为 O(m)，故入队和出队均为 O(1) 摊还时间'
        )
    ),
    JSON_OBJECT(
        'id', 'arrayqueue-performance-amortized-v1',
        'prompt', '关于 ArrayQueue 的入队和出队，哪项表述正确？',
        'options', JSON_ARRAY(
            JSON_OBJECT('id', 'QUEUE_AMORTIZED', 'text', '忽略 resize 时两者均为 O(1)；从空队列开始的一串更新中，全部 resize 总成本为 O(m)，因此两者为 O(1) 摊还时间，但单次仍可能触发 O(n) 的 resize'),
            JSON_OBJECT('id', 'QUEUE_WORST_CONSTANT', 'text', '两者每次最坏时间都是 O(1)，因为循环下标移动不需要搬移已有元素')
        ),
        'correctOptionId', 'QUEUE_AMORTIZED',
        'correctExplanation', '正确：常规入队与出队只做常数次下标计算和读写；摊还分析将一串更新中全部 resize 的 O(m) 总成本分摊后得到 O(1)，但一次复制 n 个元素的 resize 仍可能是 O(n)。',
        'incorrectExplanation', '不正确：循环数组避免了常规操作搬移元素，但容量变化时仍需复制有效元素；摊还 O(1) 不等于每次最坏 O(1)。'
    )
FROM knowledge_point point
LEFT JOIN tutor_content existing ON existing.content_key = 'ods-arrayqueue-performance' AND existing.content_version = 1
WHERE point.content_key = 'ods-arrayqueue-performance'
  AND existing.id IS NULL;
