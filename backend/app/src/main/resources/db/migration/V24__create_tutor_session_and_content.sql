-- 首个 Web Tutor 切片：内容、会话和理解检查均为服务端事实。
CREATE TABLE tutor_content (
    id BIGINT NOT NULL AUTO_INCREMENT,
    knowledge_point_id BIGINT NOT NULL,
    content_key VARCHAR(120) NOT NULL,
    content_version INT NOT NULL,
    review_status VARCHAR(40) NOT NULL,
    title VARCHAR(160) NOT NULL,
    lesson_json JSON NOT NULL,
    check_json JSON NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tutor_content_key_version (content_key, content_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='已审查 Tutor 教学内容';

CREATE TABLE tutor_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_key CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    tutor_content_id BIGINT NOT NULL,
    check_answer VARCHAR(40) DEFAULT NULL,
    check_correct TINYINT DEFAULT NULL,
    check_answered_at DATETIME DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tutor_session_key (session_key),
    KEY idx_tutor_session_user_course (user_id, course_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程 Tutor 会话';

INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-arraystack-insertion', 1, 'REVIEWED', 'ArrayStack 的按位插入',
    JSON_OBJECT('visualizationId', 'ods.arraystack-insertion.v1', 'visualizationVersion', 1,
      'summary', '插入前先确认容量足够；再将 i 到 n-1 的元素从右向左右移一格，写入 a[i]，最后令 n 增加。',
      'steps', JSON_ARRAY('检查 n < capacity；满时先扩容', '从 a[n-1] 到 a[i] 依次右移', '写入 a[i] = x', '更新 n = n + 1')),
    JSON_OBJECT('id', 'arraystack-shift-direction-v1', 'prompt', '在 a[1] 插入新元素时，后缀应按什么方向搬移以避免覆盖尚未移动的元素？',
      'options', JSON_ARRAY(JSON_OBJECT('id', 'LEFT_TO_RIGHT', 'text', '从左向右'), JSON_OBJECT('id', 'RIGHT_TO_LEFT', 'text', '从右向左')), 'correctOptionId', 'RIGHT_TO_LEFT')
FROM knowledge_point point WHERE point.content_key = 'ods-arraystack-insertion';
