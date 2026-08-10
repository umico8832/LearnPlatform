-- 收口 ArrayQueue Web Tutor 模块：五个已审查主题形成可解析的前置与后续学习路径。
UPDATE tutor_content
SET lesson_json = JSON_SET(
    lesson_json,
    '$.nextStep', JSON_OBJECT(
        'contentKey', 'ods-arrayqueue-enqueue',
        'title', 'ArrayQueue 的入队',
        'description', '继续学习如何用 j、n 与模运算定位循环数组中的队尾写入位置。'
    )
)
WHERE content_key = 'ods-arrayqueue-representation'
  AND content_version = 1
  AND review_status = 'REVIEWED';

UPDATE tutor_content
SET lesson_json = JSON_SET(
    lesson_json,
    '$.prerequisite', JSON_OBJECT(
        'contentKey', 'ods-arrayqueue-representation',
        'title', 'ArrayQueue 的循环数组表示',
        'description', '先理解 j、n 与 a[(j+k) mod capacity] 的映射，再判断队尾槽位。'
    ),
    '$.nextStep', JSON_OBJECT(
        'contentKey', 'ods-arrayqueue-dequeue',
        'title', 'ArrayQueue 的出队',
        'description', '完成入队后，继续学习如何读取队首并循环前移 j。'
    )
)
WHERE content_key = 'ods-arrayqueue-enqueue'
  AND content_version = 1
  AND review_status = 'REVIEWED';

UPDATE tutor_content
SET lesson_json = JSON_SET(
    lesson_json,
    '$.prerequisite', JSON_OBJECT(
        'contentKey', 'ods-arrayqueue-representation',
        'title', 'ArrayQueue 的循环数组表示',
        'description', '先理解队首 j 如何表示 FIFO 起点，再判断出队后的循环前移。'
    ),
    '$.nextStep', JSON_OBJECT(
        'contentKey', 'ods-arrayqueue-resize',
        'title', 'ArrayQueue 调整容量时的线性化复制',
        'description', '完成出队后，继续学习容量变化时如何保持 FIFO 逻辑顺序。'
    )
)
WHERE content_key = 'ods-arrayqueue-dequeue'
  AND content_version = 1
  AND review_status = 'REVIEWED';

UPDATE tutor_content
SET lesson_json = JSON_SET(
    lesson_json,
    '$.prerequisite', JSON_OBJECT(
        'contentKey', 'ods-arrayqueue-representation',
        'title', 'ArrayQueue 的循环数组表示',
        'description', '先确认从 j 开始的 FIFO 逻辑顺序，再将跨界元素线性化复制到新数组。'
    ),
    '$.nextStep', JSON_OBJECT(
        'contentKey', 'ods-arrayqueue-performance',
        'title', 'ArrayQueue 的操作复杂度',
        'description', '理解线性化复制后，继续分析一次 resize 与一串更新的摊还成本。'
    )
)
WHERE content_key = 'ods-arrayqueue-resize'
  AND content_version = 1
  AND review_status = 'REVIEWED';

UPDATE tutor_content
SET lesson_json = JSON_SET(
    lesson_json,
    '$.prerequisite', JSON_OBJECT(
        'contentKey', 'ods-arrayqueue-resize',
        'title', 'ArrayQueue 调整容量时的线性化复制',
        'description', '先理解 resize 为什么需要按 FIFO 顺序复制 n 个元素，再区分单次最坏成本与摊还成本。'
    )
)
WHERE content_key = 'ods-arrayqueue-performance'
  AND content_version = 1
  AND review_status = 'REVIEWED';
