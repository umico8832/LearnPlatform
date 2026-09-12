-- 第六个 Web Tutor 切片：汇总 ArrayStack 的位置相关成本与尾端摊还成本。
INSERT INTO knowledge_point (
    name, description, course_id, parent_id, content_key, content_source, content_version,
    content_review_status, sort_order, deleted
)
SELECT
    'ArrayStack 的操作复杂度',
    'get/set 的最坏时间为 O(1)；忽略 resize 时 add(i,x) 与 remove(i) 为 O(1+n-i)；尾端 push/pop 的 resize 总成本可摊还，因此为 O(1) 摊还时间。',
    course.id,
    parent.id,
    'ods-arraystack-performance',
    'AISTU',
    1,
    'REVIEWED',
    14,
    0
FROM course
JOIN knowledge_point parent ON parent.course_id = course.id
    AND parent.content_key = '408-stacks-queues-arrays'
LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-arraystack-performance'
WHERE course.content_key = 'cs408-data-structures'
  AND existing.id IS NULL;

INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-arraystack-performance', 1, 'REVIEWED', 'ArrayStack 的操作复杂度',
    JSON_OBJECT(
        'summary', 'ArrayStack 可在 O(1) 最坏时间内 get/set；忽略 resize 时，位置 i 的 add/remove 要搬移后缀而为 O(1+n-i)；尾端 push/pop 的 resize 成本可摊还，因而为 O(1) 摊还时间。',
        'prerequisite', JSON_OBJECT(
            'contentKey', 'ods-arraystack-amortized-resize',
            'title', 'ArrayStack 调整容量的摊还成本',
            'description', '先区分一次 resize 可能是 O(n) 与一串更新中 resize 总成本为 O(m)；该前置教学已可从课程目录进入。'
        ),
        'steps', JSON_ARRAY(
            'get(i) 与 set(i, x) 通过下标直接访问数组槽位，所以每次最坏时间为 O(1)',
            'add(i, x) 与 remove(i) 必须搬移下标 i 之后的后缀；忽略 resize 时为 O(1+n-i)',
            '当 i 位于尾端时，后缀长度为 0，位置搬移部分为 O(1)',
            '尾端 push/pop 偶尔触发 O(n) 的 resize，但从空结构开始的一串更新中全部 resize 成本可摊还，因此每次为 O(1) 摊还时间'
        )
    ),
    JSON_OBJECT(
        'id', 'arraystack-performance-tail-amortized-v1',
        'prompt', '关于 ArrayStack 的尾端 push/pop，哪项表述正确？',
        'options', JSON_ARRAY(
            JSON_OBJECT('id', 'TAIL_AMORTIZED', 'text', '它们省去位置搬移；虽然单次仍可能触发 O(n) 的 resize，但从空结构开始的一串操作中，每次为 O(1) 摊还时间'),
            JSON_OBJECT('id', 'TAIL_WORST_CONSTANT', 'text', '它们每次最坏时间都是 O(1)，因为尾端不需要搬移元素')
        ),
        'correctOptionId', 'TAIL_AMORTIZED',
        'correctExplanation', '正确：尾端不需要搬移后缀，但满数组扩容或低占用缩容时仍可能复制 n 个元素。摊还分析保证的是一串尾端更新的每次平均成本为 O(1)。',
        'incorrectExplanation', '不正确：尾端确实不需要位置搬移，但一次实际 resize 仍可能复制 n 个有效元素，故不能把最坏时间写成 O(1)。'
    )
FROM knowledge_point point
LEFT JOIN tutor_content existing ON existing.content_key = 'ods-arraystack-performance' AND existing.content_version = 1
WHERE point.content_key = 'ods-arraystack-performance'
  AND existing.id IS NULL;
