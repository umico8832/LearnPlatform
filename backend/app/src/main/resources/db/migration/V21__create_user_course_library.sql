-- Phase 23：稳定内容标识与个人课程库
ALTER TABLE course
    ADD COLUMN content_key VARCHAR(120) DEFAULT NULL COMMENT '跨端稳定内容标识' AFTER cover_image,
    ADD COLUMN content_source VARCHAR(40) NOT NULL DEFAULT 'PLATFORM' COMMENT '内容来源' AFTER content_key,
    ADD UNIQUE KEY uk_course_content_key (content_key);

ALTER TABLE knowledge_point
    ADD COLUMN content_key VARCHAR(120) DEFAULT NULL COMMENT '跨端稳定内容标识' AFTER parent_id,
    ADD COLUMN content_source VARCHAR(40) NOT NULL DEFAULT 'PLATFORM' COMMENT '内容来源' AFTER content_key,
    ADD UNIQUE KEY uk_knowledge_point_content_key (content_key);

CREATE TABLE user_course (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '个人课程关系ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    course_id BIGINT NOT NULL COMMENT '课程ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_course (user_id, course_id),
    KEY idx_user_course_created (user_id, create_time),
    KEY idx_user_course_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人课程库';

-- 首批课程结构取自 AiStu 的 408 数据结构知识 taxonomy；原子知识点后续按审查状态分批迁入。
INSERT INTO course (
    name, description, content_key, content_source, sort_order, status, deleted
)
SELECT
    '408 数据结构',
    '面向 408 计算机学科专业基础考试的数据结构课程',
    'cs408-data-structures',
    'AISTU',
    10,
    1,
    0
WHERE NOT EXISTS (
    SELECT 1 FROM course WHERE content_key = 'cs408-data-structures'
);

INSERT INTO knowledge_point (
    name, description, course_id, parent_id, content_key, content_source, sort_order, deleted
)
SELECT seed.name, seed.description, course.id, 0, seed.content_key, 'AISTU', seed.sort_order, 0
FROM course
JOIN (
    SELECT '408 数据结构考纲与复习指南' AS name,
           '课程定位、考纲范围与训练闭环' AS description,
           '408-data-structures-exam-guide' AS content_key, 1 AS sort_order
    UNION ALL SELECT '基本概念', '数据结构、算法与复杂度基础', '408-basic-concepts', 2
    UNION ALL SELECT '线性表', '线性表的逻辑结构、存储与应用', '408-linear-lists', 3
    UNION ALL SELECT '栈、队列和数组', '栈、队列、数组及其典型应用', '408-stacks-queues-arrays', 4
    UNION ALL SELECT '树和二叉树', '树、二叉树及其应用', '408-trees', 5
    UNION ALL SELECT '图', '图的存储、遍历与典型算法', '408-graphs', 6
    UNION ALL SELECT '查找', '线性、树型、散列与字符串查找', '408-searching', 7
    UNION ALL SELECT '排序', '内部排序、外部排序与算法分析', '408-sorting', 8
) AS seed
LEFT JOIN knowledge_point existing ON existing.content_key = seed.content_key
WHERE course.content_key = 'cs408-data-structures'
  AND existing.id IS NULL;
