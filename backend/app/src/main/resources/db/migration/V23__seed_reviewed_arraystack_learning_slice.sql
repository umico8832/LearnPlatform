-- Phase 23：首个已审查的 AiStu 原子知识切片。它只迁入可验证内容，不含桌面运行时依赖。
ALTER TABLE knowledge_point
    ADD COLUMN content_version INT DEFAULT NULL COMMENT '迁入内容版本' AFTER content_source,
    ADD COLUMN content_review_status VARCHAR(40) DEFAULT NULL COMMENT '迁入时的内容或课件审查状态' AFTER content_version;

INSERT INTO knowledge_point (
    name, description, course_id, parent_id, content_key, content_source, content_version,
    content_review_status, sort_order, deleted
)
SELECT
    'ArrayStack 按位插入',
    '容量检查、后缀从右向左搬移、写入新元素与数量更新；用于解释覆盖风险和数组顺序表插入。',
    course.id,
    parent.id,
    'ods-arraystack-insertion',
    'AISTU',
    1,
    'REVIEWED',
    10,
    0
FROM course
JOIN knowledge_point parent ON parent.course_id = course.id
    AND parent.content_key = '408-stacks-queues-arrays'
LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-arraystack-insertion'
WHERE course.content_key = 'cs408-data-structures'
  AND existing.id IS NULL;
