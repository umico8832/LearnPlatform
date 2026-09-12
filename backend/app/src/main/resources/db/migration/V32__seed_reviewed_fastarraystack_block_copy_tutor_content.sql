-- 第七个 Web Tutor 切片：独立审查 FastArrayStack 的批量复制优化，保留常数与渐近复杂度边界。
INSERT INTO knowledge_point (
    name, description, course_id, parent_id, content_key, content_source, content_version,
    content_review_status, sort_order, deleted
)
SELECT
    'FastArrayStack 的批量复制优化',
    '用运行环境的批量复制或移动函数替代逐元素循环，降低连续区间搬移的常数开销；所需搬移元素数不变，因此不改变渐近时间复杂度。',
    course.id,
    parent.id,
    'ods-fastarraystack-block-copy',
    'AISTU',
    1,
    'REVIEWED',
    15,
    0
FROM course
JOIN knowledge_point parent ON parent.course_id = course.id
    AND parent.content_key = '408-stacks-queues-arrays'
LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-fastarraystack-block-copy'
WHERE course.content_key = 'cs408-data-structures'
  AND existing.id IS NULL;

INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-fastarraystack-block-copy', 1, 'REVIEWED', 'FastArrayStack 的批量复制优化',
    JSON_OBJECT(
        'summary', 'FastArrayStack 用运行环境的批量复制或移动函数处理插入、删除和 resize 中的连续区间，减少逐元素循环的常数开销；搬移元素数不变，所以渐近时间复杂度不变。',
        'prerequisite', JSON_OBJECT(
            'contentKey', 'ods-arraystack-insertion',
            'title', 'ArrayStack 的按位插入',
            'description', '先理解按位插入需要搬移连续后缀；批量复制只替换这段搬移的实现方式，不改变其搬移范围。该前置教学已可从课程目录进入。'
        ),
        'steps', JSON_ARRAY(
            '识别插入、删除和 resize 中需要连续搬移或复制的元素区间',
            '用运行环境提供的批量复制或移动函数替代显式逐元素循环',
            '批量函数仍处理相同数量的元素，因此不改变原有的渐近时间复杂度',
            '底层实现可能降低常数开销，但实际收益取决于语言、元素类型、库实现和硬件'
        )
    ),
    JSON_OBJECT(
        'id', 'fastarraystack-block-copy-complexity-v1',
        'prompt', 'FastArrayStack 用批量复制函数替代逐元素循环后，哪项表述正确？',
        'options', JSON_ARRAY(
            JSON_OBJECT('id', 'CONSTANT_FACTOR_ONLY', 'text', '它可能降低连续搬移的常数开销，但仍需处理相同数量的元素，渐近复杂度不变'),
            JSON_OBJECT('id', 'ASYMPTOTIC_CONSTANT', 'text', '它使任意位置的插入、删除和 resize 都变为最坏 O(1)')
        ),
        'correctOptionId', 'CONSTANT_FACTOR_ONLY',
        'correctExplanation', '正确：批量复制可能利用优化实现降低常数开销，但连续区间中仍有同样数量的元素需要处理，不能据此改变大 O。',
        'incorrectExplanation', '不正确：批量复制不会消除需要搬移或复制的元素数量；任意位置的更新和一次 resize 仍可能处理线性数量的元素。'
    )
FROM knowledge_point point
LEFT JOIN tutor_content existing ON existing.content_key = 'ods-fastarraystack-block-copy' AND existing.content_version = 1
WHERE point.content_key = 'ods-fastarraystack-block-copy'
  AND existing.id IS NULL;
