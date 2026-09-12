-- 互动课件是已审查内容的一部分；仅声明受限参数，不存放脚本或客户端可执行代码。
UPDATE tutor_content
SET lesson_json = JSON_SET(
    lesson_json,
    '$.visualization', JSON_OBJECT(
        'kind', 'ARRAY_STACK_INSERTION',
        'version', 1,
        'capacity', 5,
        'initialElements', JSON_ARRAY('A', 'B', 'C'),
        'insertIndex', 1,
        'insertValue', 'X'
    )
)
WHERE content_key = 'ods-arraystack-insertion'
  AND content_version = 1
  AND review_status = 'REVIEWED';
