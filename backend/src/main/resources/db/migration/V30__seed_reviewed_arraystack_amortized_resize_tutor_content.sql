-- 第五个 Web Tutor 切片：独立审查容量调整的摊还成本，不将其误写为每次 resize 的最坏成本。
INSERT INTO knowledge_point (
    name, description, course_id, parent_id, content_key, content_source, content_version,
    content_review_status, sort_order, deleted
)
SELECT
    'ArrayStack 调整容量的摊还成本',
    '单次 resize 仍可能复制 n 个元素而耗时 O(n)，但从空结构开始的 m 次 add/remove 中全部 resize 总成本为 O(m)。',
    course.id,
    parent.id,
    'ods-arraystack-amortized-resize',
    'AISTU',
    1,
    'REVIEWED',
    13,
    0
FROM course
JOIN knowledge_point parent ON parent.course_id = course.id
    AND parent.content_key = '408-stacks-queues-arrays'
LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-arraystack-amortized-resize'
WHERE course.content_key = 'cs408-data-structures'
  AND existing.id IS NULL;

INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-arraystack-amortized-resize', 1, 'REVIEWED', 'ArrayStack 调整容量的摊还成本',
    JSON_OBJECT(
        'summary', '单次 resize 复制 n 个元素，最坏耗时仍是 O(n)；但两次足够昂贵的容量调整之间会积累同阶的 add/remove，因此从空结构开始的 m 次更新中全部 resize 总成本为 O(m)。',
        'prerequisite', JSON_OBJECT(
            'contentKey', 'ods-arraystack-resize',
            'title', 'ArrayStack 的容量调整',
            'description', '先确认一次 resize 会分配新数组并复制 n 个有效元素，因此单次触发时可能耗时 O(n)。可从课程目录进入该前置教学。'
        ),
        'nextStep', JSON_OBJECT(
            'contentKey', 'ods-arraystack-performance',
            'title', 'ArrayStack 的操作复杂度',
            'description', '再结合按位插入、删除与容量调整，区分位置相关的最坏成本和尾端 push/pop 的摊还 O(1)。该独立知识尚未迁入为可打开的 Web 教学内容。'
        ),
        'steps', JSON_ARRAY(
            '一次 resize 需要复制当前 n 个有效元素，所以该次操作本身可能耗时 O(n)',
            '调整后容量与 n 保持固定比例，不会紧接着再次触发同规模复制',
            '再次达到扩容或缩容阈值前，必须先发生与当前 n 同阶的 add 或 remove',
            '把每次线性复制成本分摊给其间的更新，m 次更新的全部 resize 总成本为 O(m)'
        )
    ),
    JSON_OBJECT(
        'id', 'arraystack-amortized-resize-total-cost-v1',
        'prompt', '从空 ArrayStack 开始执行 m 次 add/remove。关于其中全部 resize 的成本，哪项正确？',
        'options', JSON_ARRAY(
            JSON_OBJECT('id', 'TOTAL_LINEAR', 'text', '总 resize 成本为 O(m)，但单次触发 resize 仍可能是 O(n)'),
            JSON_OBJECT('id', 'EACH_CONSTANT', 'text', '每次触发 resize 都是 O(1)，因为已经做了摊还分析')
        ),
        'correctOptionId', 'TOTAL_LINEAR',
        'correctExplanation', '正确：摊还界约束的是操作序列的总 resize 成本；一次实际复制 n 个元素的 resize 仍可能耗时 O(n)。',
        'incorrectExplanation', '不正确：摊还 O(1) 不会把一次实际发生的 n 次复制变成最坏 O(1)；它说明这些昂贵调整在一串更新中的总成本为线性。'
    )
FROM knowledge_point point
LEFT JOIN tutor_content existing ON existing.content_key = 'ods-arraystack-amortized-resize' AND existing.content_version = 1
WHERE point.content_key = 'ods-arraystack-amortized-resize'
  AND existing.id IS NULL;
