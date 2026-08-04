-- 已审查的 ArrayStack 教学切片只增加路径提示，不迁入尚未审查的独立原子知识内容。
UPDATE tutor_content
SET lesson_json = JSON_SET(
    lesson_json,
    '$.prerequisite', JSON_OBJECT(
        'contentKey', 'ods-array-size-capacity',
        'title', '元素数量与数组容量',
        'description', '先区分有效元素数量 n 与后备数组容量：只有 n 个槽位存放逻辑元素，且必须满足 n 不超过容量。该独立知识尚未迁入为可打开的 Web 教学内容。'
    ),
    '$.nextStep', JSON_OBJECT(
        'contentKey', 'ods-arraystack-performance',
        'title', 'ArrayStack 的操作复杂度',
        'description', '理解右移后，再分析插入位置 i 与后缀长度 n - i 如何决定一次按位插入的搬移成本。该独立知识尚未迁入为可打开的 Web 教学内容。'
    )
)
WHERE content_key = 'ods-arraystack-insertion'
  AND content_version = 1
  AND review_status = 'REVIEWED';
