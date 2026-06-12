-- AI 题库与错题复习系统 - 建表 SQL
-- 数据库：MySQL 8.0+
-- 字符集：utf8mb4

CREATE DATABASE IF NOT EXISTS `learn_platform` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `learn_platform`;

-- ========================================
-- 1. 用户表
-- ========================================
CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
  `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：USER-普通用户 ADMIN-管理员',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- ========================================
-- 2. 课程表
-- ========================================
CREATE TABLE IF NOT EXISTS `course` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '课程ID',
  `name` VARCHAR(100) NOT NULL COMMENT '课程名称',
  `description` TEXT DEFAULT NULL COMMENT '课程描述',
  `cover_image` VARCHAR(500) DEFAULT NULL COMMENT '封面图URL',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程表';

-- ========================================
-- 3. 知识点表
-- ========================================
CREATE TABLE IF NOT EXISTS `knowledge_point` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '知识点ID',
  `name` VARCHAR(100) NOT NULL COMMENT '知识点名称',
  `description` TEXT DEFAULT NULL COMMENT '知识点描述',
  `course_id` BIGINT NOT NULL COMMENT '所属课程ID',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父知识点ID，0表示顶级',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识点表';

-- ========================================
-- 4. 题目表
-- ========================================
CREATE TABLE IF NOT EXISTS `question` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '题目ID',
  `content` TEXT NOT NULL COMMENT '题干内容（支持Markdown）',
  `question_type` VARCHAR(20) NOT NULL COMMENT '题型：SINGLE_CHOICE-单选 MULTIPLE_CHOICE-多选 TRUE_FALSE-判断 FILL_BLANK-填空 SHORT_ANSWER-简答',
  `course_id` BIGINT NOT NULL COMMENT '所属课程ID',
  `difficulty` TINYINT NOT NULL DEFAULT 3 COMMENT '难度等级：1-5',
  `analysis` TEXT DEFAULT NULL COMMENT '题目解析',
  `tags` VARCHAR(500) DEFAULT NULL COMMENT '标签，逗号分隔',
  `score` INT NOT NULL DEFAULT 1 COMMENT '分值',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `create_by` BIGINT DEFAULT NULL COMMENT '创建者ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_question_type` (`question_type`),
  KEY `idx_difficulty` (`difficulty`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题目表';

-- ========================================
-- 5. 题目选项表
-- ========================================
CREATE TABLE IF NOT EXISTS `question_option` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '选项ID',
  `question_id` BIGINT NOT NULL COMMENT '所属题目ID',
  `content` VARCHAR(1000) NOT NULL COMMENT '选项内容',
  `option_label` VARCHAR(10) NOT NULL COMMENT '选项标签：A/B/C/D等',
  `is_correct` TINYINT NOT NULL DEFAULT 0 COMMENT '是否正确答案：0-否 1-是',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题目选项表';

-- ========================================
-- 6. 题目-知识点关联表
-- ========================================
CREATE TABLE IF NOT EXISTS `question_knowledge_point` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `knowledge_point_id` BIGINT NOT NULL COMMENT '知识点ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_kp` (`question_id`, `knowledge_point_id`),
  KEY `idx_question_id` (`question_id`),
  KEY `idx_knowledge_point_id` (`knowledge_point_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题目-知识点关联表';

-- ========================================
-- 7. 刷题记录表
-- ========================================
CREATE TABLE IF NOT EXISTS `practice_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `user_answer` VARCHAR(1000) NOT NULL COMMENT '用户答案',
  `is_correct` TINYINT NOT NULL COMMENT '是否正确：0-错误 1-正确',
  `answer_time` INT DEFAULT NULL COMMENT '答题耗时（秒）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '答题时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_question_id` (`question_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='刷题记录表';

-- ========================================
-- 8. 错题本表
-- ========================================
CREATE TABLE IF NOT EXISTS `wrong_question` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '错题记录ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `wrong_count` INT NOT NULL DEFAULT 1 COMMENT '答错次数',
  `mastery_level` TINYINT NOT NULL DEFAULT 0 COMMENT '掌握程度：0-未掌握 1-部分掌握 2-已掌握',
  `last_wrong_answer` VARCHAR(1000) DEFAULT NULL COMMENT '最近一次错误答案',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次答错时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-保留 1-移出错题本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_question` (`user_id`, `question_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='错题本表';

-- ========================================
-- 9. 试卷表
-- ========================================
CREATE TABLE IF NOT EXISTS `exam_paper` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '试卷ID',
  `title` VARCHAR(200) NOT NULL COMMENT '试卷标题',
  `description` TEXT DEFAULT NULL COMMENT '试卷描述',
  `course_id` BIGINT DEFAULT NULL COMMENT '所属课程ID',
  `total_score` INT NOT NULL DEFAULT 0 COMMENT '总分',
  `duration` INT NOT NULL DEFAULT 60 COMMENT '考试时长（分钟）',
  `question_count` INT NOT NULL DEFAULT 0 COMMENT '题目数量',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-草稿 1-已发布',
  `create_by` BIGINT DEFAULT NULL COMMENT '创建者ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='试卷表';

-- ========================================
-- 10. 试卷-题目关联表
-- ========================================
CREATE TABLE IF NOT EXISTS `exam_question` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `exam_paper_id` BIGINT NOT NULL COMMENT '试卷ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '题目在试卷中的顺序',
  `score` INT NOT NULL DEFAULT 1 COMMENT '该题在本卷中的分值',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_question` (`exam_paper_id`, `question_id`),
  KEY `idx_exam_paper_id` (`exam_paper_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='试卷-题目关联表';

-- ========================================
-- 11. 考试记录表
-- ========================================
CREATE TABLE IF NOT EXISTS `exam_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '考试记录ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `exam_paper_id` BIGINT NOT NULL COMMENT '试卷ID',
  `start_time` DATETIME NOT NULL COMMENT '开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `score` INT DEFAULT NULL COMMENT '得分',
  `total_score` INT DEFAULT NULL COMMENT '总分',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-进行中 1-已完成 2-已超时',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_exam_paper_id` (`exam_paper_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考试记录表';

-- ========================================
-- 12. 考试答题详情表
-- ========================================
CREATE TABLE IF NOT EXISTS `exam_answer` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '答题记录ID',
  `exam_record_id` BIGINT NOT NULL COMMENT '考试记录ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `user_answer` VARCHAR(1000) NOT NULL COMMENT '用户答案',
  `is_correct` TINYINT DEFAULT NULL COMMENT '是否正确：0-错误 1-正确',
  `score` INT DEFAULT NULL COMMENT '该题得分',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '答题时间',
  PRIMARY KEY (`id`),
  KEY `idx_exam_record_id` (`exam_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考试答题详情表';

-- ========================================
-- 13. AI 调用日志表（后期）
-- ========================================
CREATE TABLE IF NOT EXISTS `ai_call_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '调用用户ID',
  `function_type` VARCHAR(50) NOT NULL COMMENT '功能类型：EXPLANATION/VARIANT/REVIEW/SUMMARY/GRADE',
  `request_params` TEXT DEFAULT NULL COMMENT '请求参数（JSON）',
  `response_content` TEXT DEFAULT NULL COMMENT '响应内容',
  `model` VARCHAR(100) DEFAULT NULL COMMENT '使用的模型',
  `tokens_used` INT DEFAULT NULL COMMENT 'Token用量',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-失败 1-成功',
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  `duration` INT DEFAULT NULL COMMENT '调用耗时（毫秒）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_function_type` (`function_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI调用日志表';

-- ========================================
-- 初始测试数据
-- ========================================

-- 管理员账号（密码: admin123）
INSERT INTO `user` (`username`, `password`, `nickname`, `role`, `status`) VALUES
('admin', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36PsLMZGp0XPSFLEHKZkQXG', '管理员', 'ADMIN', 1);

-- 测试用户（密码: test123）
INSERT INTO `user` (`username`, `password`, `nickname`, `role`, `status`) VALUES
('testuser', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36PsLMZGp0XPSFLEHKZkQXG', '测试用户', 'USER', 1);

-- 示例课程
INSERT INTO `course` (`name`, `description`, `sort_order`) VALUES
('Java 基础', 'Java 编程语言基础知识，包括语法、面向对象、异常处理等', 1),
('数据结构与算法', '常见数据结构和算法的学习与练习', 2),
('数据库原理', '关系型数据库原理、SQL 语法、索引优化等', 3),
('计算机网络', 'TCP/IP、HTTP、网络协议等计算机网络基础知识', 4),
('操作系统', '进程管理、内存管理、文件系统等操作系统核心知识', 5);