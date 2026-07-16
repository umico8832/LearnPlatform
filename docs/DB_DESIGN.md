# AI 题库与错题复习系统 - 数据库设计文档

## 一、数据库概述

- **数据库类型**：MySQL 8.0+
- **字符集**：utf8mb4
- **排序规则**：utf8mb4_general_ci
- **命名规范**：表名和字段名使用 snake_case
- **通用字段**：所有表包含 `create_time`、`update_time`、`deleted` 字段
- **逻辑删除**：使用 MyBatis-Plus 的 `@TableLogic` 实现逻辑删除

---

## 二、ER 关系图（文字版）

```
User (1) ──── (N) PracticeRecord
User (1) ──── (N) WrongQuestion
User (1) ──── (N) ExamRecord
User (1) ──── (N) QuestionSubmission

Course (1) ──── (N) KnowledgePoint
Course (1) ──── (N) Question
Course (1) ──── (N) QuestionSubmission

KnowledgePoint (1) ──── (N) KnowledgePoint (自引用父子关系)
KnowledgePoint (N) ──── (N) Question (通过 question_knowledge_point 中间表)

Question (1) ──── (N) QuestionOption
Question (1) ──── (N) PracticeRecord
Question (1) ──── (N) WrongQuestion
Question (1) ──── (N) QuestionSubmission (通过 imported_question_id 记录入库结果)
Question (1) ──── (N) QuestionVersion
Question (1) ──── (N) AiAssetView
Question (N) ──── (N) ExamPaper (通过 exam_question 关联)

ExamPaper (1) ──── (N) ExamQuestion
ExamPaper (1) ──── (N) ExamRecord

ExamRecord (1) ──── (N) ExamAnswer
```

---

## 三、表结构详细设计

### 3.1 用户表 (user)

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|:----:|--------|------|
| id | BIGINT | 是 | 自增主键 | 用户ID |
| username | VARCHAR(50) | 是 | | 用户名，唯一 |
| password | VARCHAR(255) | 是 | | 密码（BCrypt 加密） |
| nickname | VARCHAR(50) | 否 | | 昵称 |
| avatar | VARCHAR(500) | 否 | | 头像 URL |
| role | VARCHAR(20) | 是 | 'USER' | 角色：USER / ADMIN |
| status | TINYINT | 是 | 1 | 状态：0-禁用 1-启用 |
| ai_daily_quota | INT | 否 | NULL | 用户级 AI 每日调用配额；NULL 继承 `ai.daily-quota` 全局配置，0 表示不限次数 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-未删除 1-已删除 |

**索引**：
- UNIQUE INDEX `uk_username` ON `username`

**建表 SQL**：
```sql
CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
  `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：USER-普通用户 ADMIN-管理员',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `ai_daily_quota` INT DEFAULT NULL COMMENT '用户级AI每日调用配额，NULL继承全局配置，0不限次数',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';
```

---

### 3.2 课程表 (course)

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|:----:|--------|------|
| id | BIGINT | 是 | 自增主键 | 课程ID |
| name | VARCHAR(100) | 是 | | 课程名称 |
| description | TEXT | 否 | | 课程描述 |
| cover_image | VARCHAR(500) | 否 | | 封面图 URL |
| sort_order | INT | 是 | 0 | 排序序号 |
| status | TINYINT | 是 | 1 | 状态：0-禁用 1-启用 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除 |

**建表 SQL**：
```sql
CREATE TABLE `course` (
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
```

---

### 3.3 知识点表 (knowledge_point)

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|:----:|--------|------|
| id | BIGINT | 是 | 自增主键 | 知识点ID |
| name | VARCHAR(100) | 是 | | 知识点名称 |
| description | TEXT | 否 | | 知识点描述 |
| course_id | BIGINT | 是 | | 所属课程ID |
| parent_id | BIGINT | 否 | 0 | 父知识点ID，0表示顶级 |
| sort_order | INT | 是 | 0 | 排序序号 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除 |

**索引**：
- INDEX `idx_course_id` ON `course_id`
- INDEX `idx_parent_id` ON `parent_id`

**建表 SQL**：
```sql
CREATE TABLE `knowledge_point` (
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
```

---

### 3.4 题目表 (question)

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|:----:|--------|------|
| id | BIGINT | 是 | 自增主键 | 题目ID |
| content | TEXT | 是 | | 题干内容（支持 Markdown） |
| question_type | VARCHAR(20) | 是 | | 题型：SINGLE_CHOICE / MULTIPLE_CHOICE / TRUE_FALSE / FILL_BLANK / SHORT_ANSWER |
| course_id | BIGINT | 是 | | 所属课程ID |
| difficulty | TINYINT | 是 | 3 | 难度等级：1-5 |
| analysis | TEXT | 否 | | 题目解析 |
| tags | VARCHAR(500) | 否 | | 标签，逗号分隔 |
| score | INT | 是 | 1 | 分值（默认1分） |
| status | TINYINT | 是 | 1 | 状态：0-禁用 1-启用 |
| create_by | BIGINT | 否 | | 创建者ID |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除 |

**索引**：
- INDEX `idx_course_id` ON `course_id`
- INDEX `idx_question_type` ON `question_type`
- INDEX `idx_difficulty` ON `difficulty`
- INDEX `idx_status` ON `status`

**建表 SQL**：
```sql
CREATE TABLE `question` (
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
```

---

### 3.5 题目选项表 (question_option)

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|:----:|--------|------|
| id | BIGINT | 是 | 自增主键 | 选项ID |
| question_id | BIGINT | 是 | | 所属题目ID |
| content | VARCHAR(1000) | 是 | | 选项内容 |
| option_label | VARCHAR(10) | 是 | | 选项标签：A / B / C / D |
| is_correct | TINYINT | 是 | 0 | 是否正确答案：0-否 1-是 |
| sort_order | INT | 是 | 0 | 排序序号 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除 |

**索引**：
- INDEX `idx_question_id` ON `question_id`

**建表 SQL**：
```sql
CREATE TABLE `question_option` (
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
```

---

### 3.6 题目-知识点关联表 (question_knowledge_point)

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|:----:|--------|------|
| id | BIGINT | 是 | 自增主键 | 主键ID |
| question_id | BIGINT | 是 | | 题目ID |
| knowledge_point_id | BIGINT | 是 | | 知识点ID |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |

**索引**：
- INDEX `idx_question_id` ON `question_id`
- INDEX `idx_knowledge_point_id` ON `knowledge_point_id`
- UNIQUE INDEX `uk_question_kp` ON (`question_id`, `knowledge_point_id`)

**建表 SQL**：
```sql
CREATE TABLE `question_knowledge_point` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `knowledge_point_id` BIGINT NOT NULL COMMENT '知识点ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_kp` (`question_id`, `knowledge_point_id`),
  KEY `idx_question_id` (`question_id`),
  KEY `idx_knowledge_point_id` (`knowledge_point_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题目-知识点关联表';
```

---

### 3.7 刷题记录表 (practice_record)

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|:----:|--------|------|
| id | BIGINT | 是 | 自增主键 | 记录ID |
| user_id | BIGINT | 是 | | 用户ID |
| question_id | BIGINT | 是 | | 题目ID |
| user_answer | VARCHAR(1000) | 是 | | 用户答案 |
| is_correct | TINYINT | 是 | | 是否正确：0-错误 1-正确 |
| answer_time | INT | 否 | | 答题耗时（秒） |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 答题时间 |

**索引**：
- INDEX `idx_user_id` ON `user_id`
- INDEX `idx_question_id` ON `question_id`
- INDEX `idx_create_time` ON `create_time`

**建表 SQL**：
```sql
CREATE TABLE `practice_record` (
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
```

---

### 3.8 错题本表 (wrong_question)

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|:----:|--------|------|
| id | BIGINT | 是 | 自增主键 | 错题记录ID |
| user_id | BIGINT | 是 | | 用户ID |
| question_id | BIGINT | 是 | | 题目ID |
| wrong_count | INT | 是 | 1 | 答错次数 |
| mastery_level | TINYINT | 是 | 0 | 掌握程度：0-未掌握 1-部分掌握 2-已掌握 |
| last_wrong_answer | VARCHAR(1000) | 否 | | 最近一次错误答案 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 首次答错时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除（手动移出错题本） |

**索引**：
- INDEX `idx_user_id` ON `user_id`
- INDEX `idx_question_id` ON `question_id`
- UNIQUE INDEX `uk_user_question` ON (`user_id`, `question_id`)

**建表 SQL**：
```sql
CREATE TABLE `wrong_question` (
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
```

---

### 3.9 试卷表 (exam_paper)

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|:----:|--------|------|
| id | BIGINT | 是 | 自增主键 | 试卷ID |
| title | VARCHAR(200) | 是 | | 试卷标题 |
| description | TEXT | 否 | | 试卷描述 |
| course_id | BIGINT | 否 | | 所属课程ID |
| total_score | INT | 是 | 0 | 总分 |
| duration | INT | 是 | 60 | 考试时长（分钟） |
| question_count | INT | 是 | 0 | 题目数量 |
| status | TINYINT | 是 | 0 | 状态：0-草稿 1-已发布 |
| create_by | BIGINT | 否 | | 创建者ID |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除 |

**建表 SQL**：
```sql
CREATE TABLE `exam_paper` (
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
```

---

### 3.10 试卷-题目关联表 (exam_question)

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|:----:|--------|------|
| id | BIGINT | 是 | 自增主键 | 主键ID |
| exam_paper_id | BIGINT | 是 | | 试卷ID |
| question_id | BIGINT | 是 | | 题目ID |
| sort_order | INT | 是 | 0 | 题目在试卷中的顺序 |
| score | INT | 是 | 1 | 该题在本卷中的分值 |

**索引**：
- INDEX `idx_exam_paper_id` ON `exam_paper_id`
- UNIQUE INDEX `uk_paper_question` ON (`exam_paper_id`, `question_id`)

**建表 SQL**：
```sql
CREATE TABLE `exam_question` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `exam_paper_id` BIGINT NOT NULL COMMENT '试卷ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '题目在试卷中的顺序',
  `score` INT NOT NULL DEFAULT 1 COMMENT '该题在本卷中的分值',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_question` (`exam_paper_id`, `question_id`),
  KEY `idx_exam_paper_id` (`exam_paper_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='试卷-题目关联表';
```

---

### 3.11 考试记录表 (exam_record)

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|:----:|--------|------|
| id | BIGINT | 是 | 自增主键 | 考试记录ID |
| user_id | BIGINT | 是 | | 用户ID |
| exam_paper_id | BIGINT | 是 | | 试卷ID |
| start_time | DATETIME | 是 | | 开始时间 |
| end_time | DATETIME | 否 | | 结束时间 |
| score | INT | 否 | | 得分 |
| total_score | INT | 否 | | 总分 |
| status | TINYINT | 是 | 0 | 状态：0-进行中 1-已完成 2-已超时 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

**索引**：
- INDEX `idx_user_id` ON `user_id`
- INDEX `idx_exam_paper_id` ON `exam_paper_id`

**建表 SQL**：
```sql
CREATE TABLE `exam_record` (
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
```

---

### 3.12 考试答题详情表 (exam_answer)

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|:----:|--------|------|
| id | BIGINT | 是 | 自增主键 | 答题记录ID |
| exam_record_id | BIGINT | 是 | | 考试记录ID |
| question_id | BIGINT | 是 | | 题目ID |
| user_answer | VARCHAR(1000) | 是 | | 用户答案 |
| is_correct | TINYINT | 否 | | 是否正确：0-错误 1-正确 |
| score | INT | 否 | | 该题得分 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 答题时间 |

**索引**：
- INDEX `idx_exam_record_id` ON `exam_record_id`
- UNIQUE INDEX `uk_record_question` ON (`exam_record_id`, `question_id`)

**建表 SQL**：
```sql
CREATE TABLE `exam_answer` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '答题记录ID',
  `exam_record_id` BIGINT NOT NULL COMMENT '考试记录ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `user_answer` VARCHAR(1000) NOT NULL COMMENT '用户答案',
  `is_correct` TINYINT DEFAULT NULL COMMENT '是否正确：0-错误 1-正确',
  `score` INT DEFAULT NULL COMMENT '该题得分',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '答题时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_question` (`exam_record_id`, `question_id`),
  KEY `idx_exam_record_id` (`exam_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考试答题详情表';
```

---

### 3.13 AI 调用日志表 (ai_call_log) - 后期

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|:----:|--------|------|
| id | BIGINT | 是 | 自增主键 | 日志ID |
| user_id | BIGINT | 否 | | 调用用户ID |
| function_type | VARCHAR(50) 是 | | 功能类型：EXPLANATION / VARIANT / REVIEW / SUMMARY / GRADE |
| request_params | TEXT | 否 | | 请求参数（JSON） |
| response_content | TEXT | 否 | | 响应内容 |
| model | VARCHAR(100) | 否 | | 使用的模型 |
| tokens_used | INT | 否 | | 上游响应返回的 total tokens；上游未返回 usage 时保持 NULL，禁止本地估算 |
| prompt_tokens | INT | 否 | | 上游响应返回的输入 tokens；缺失时保持 NULL |
| completion_tokens | INT | 否 | | 上游响应返回的输出 tokens；缺失时保持 NULL |
| cost_usd | DECIMAL(16,8) | 否 | | 调用时按配置模型单价计算的 USD 成本；仅在输入/输出 token 完整且价格已配置时写入 |
| status | TINYINT | 是 | 1 | 状态：0-失败 1-成功 |
| error_message | VARCHAR(1000) | 否 | | 错误信息 |
| duration | INT | 否 | | 调用耗时（毫秒） |
| trace_id | VARCHAR(32) | 否 | | 发起调用的 HTTP 请求追踪 ID；非 HTTP / 异步调用可为空 |
| prompt_template | VARCHAR(100) | 否 | | Prompt 模板或功能标识，不包含原始提示词内容 |
| prompt_hash | CHAR(64) | 否 | | system/user prompt 的 SHA-256 指纹，用于版本比对且不可反推原文 |
| model_config_version | CHAR(64) | 否 | | 调用时模型相关配置指纹，覆盖模型名、maxTokens、stream usage 和该模型价格配置 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |

**建表 SQL**：
```sql
CREATE TABLE `ai_call_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '调用用户ID',
  `function_type` VARCHAR(50) NOT NULL COMMENT '功能类型：EXPLANATION/VARIANT/REVIEW/SUMMARY/GRADE',
  `request_params` TEXT DEFAULT NULL COMMENT '请求参数（JSON）',
  `response_content` TEXT DEFAULT NULL COMMENT '响应内容',
  `model` VARCHAR(100) DEFAULT NULL COMMENT '使用的模型',
  `tokens_used` INT DEFAULT NULL COMMENT 'Token用量',
  `prompt_tokens` INT DEFAULT NULL COMMENT '上游返回的输入tokens',
  `completion_tokens` INT DEFAULT NULL COMMENT '上游返回的输出tokens',
  `cost_usd` DECIMAL(16,8) DEFAULT NULL COMMENT '按调用时模型单价计算的USD成本',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-失败 1-成功',
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  `duration` INT DEFAULT NULL COMMENT '调用耗时（毫秒）',
  `trace_id` VARCHAR(32) DEFAULT NULL COMMENT '关联HTTP请求追踪ID',
  `prompt_template` VARCHAR(100) DEFAULT NULL COMMENT 'Prompt模板或功能标识，不含原始提示词内容',
  `prompt_hash` CHAR(64) DEFAULT NULL COMMENT 'system/user prompt的SHA-256指纹，不可反推出原文',
  `model_config_version` CHAR(64) DEFAULT NULL COMMENT '调用时模型相关配置指纹',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_function_type` (`function_type`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_trace_id` (`trace_id`),
  KEY `idx_prompt_template` (`prompt_template`),
  KEY `idx_model_config_version` (`model_config_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI调用日志表';
```

### 3.13.1 AI 配额调整审计表 (ai_quota_audit_log)

管理员修改用户级 AI 日配额时写入一条不可变记录；配额更新与审计插入在同一事务内完成。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:----:|------|
| id | BIGINT | 是 | 自增主键 |
| user_id | BIGINT | 是 | 被调整配额的用户 ID |
| admin_user_id | BIGINT | 是 | 执行调整的管理员 ID |
| previous_daily_quota | INT | 否 | 调整前值；NULL 表示继承全局配置 |
| new_daily_quota | INT | 否 | 调整后值；NULL 表示继承全局配置，0 表示不限次数 |
| reason | VARCHAR(500) | 是 | 调整原因 |
| create_time | DATETIME | 是 | 创建时间 |

**索引**：`idx_user_create_time (user_id, create_time)`、`idx_admin_create_time (admin_user_id, create_time)`。

### 3.13.2 AI 运营提醒表 (ai_usage_alert)

管理端 AI 运营报告命中高失败率、失败率突增、延迟突增或调用量突增时写入未确认提醒；同类型、同周期天数、同一天生成的未确认提醒会更新同一条记录。管理员确认后记录确认人和确认时间。可选 webhook 站外通知不新增表字段，仅在新提醒插入后按配置发送结构化 payload。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:----:|------|
| id | BIGINT | 是 | 自增主键 |
| level | VARCHAR(20) | 是 | 提醒级别：INFO / WARNING |
| alert_type | VARCHAR(50) | 是 | 提醒类型，如 HIGH_FAILURE_RATE |
| message | VARCHAR(500) | 是 | 提醒内容 |
| period_days | INT | 是 | 统计周期天数 |
| period_start | DATETIME | 是 | 当前统计周期开始时间 |
| period_end | DATETIME | 是 | 当前统计周期结束时间 |
| metric_snapshot | TEXT | 否 | 触发提醒时的关键指标 JSON 快照 |
| status | VARCHAR(20) | 是 | OPEN / ACKNOWLEDGED |
| acknowledged_by | BIGINT | 否 | 确认提醒的管理员 ID |
| acknowledged_time | DATETIME | 否 | 确认时间 |
| create_time | DATETIME | 是 | 创建时间 |
| update_time | DATETIME | 是 | 更新时间 |
| deleted | TINYINT | 是 | 逻辑删除 |

**索引**：`idx_status_period (status, period_end)`、`idx_type_period (alert_type, period_start, period_end)`、`idx_acknowledged_by (acknowledged_by)`。

### 3.14 题目 AI 学习资产表 (question_ai_asset) - Phase 13

存储 AI 生成的结构化学习资产缓存，避免对同一题同一类型重复调用 AI。

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|:----:|--------|------|
| id | BIGINT | 是 | 自增主键 | 资产ID |
| question_id | BIGINT | 是 | | 题目ID |
| asset_type | VARCHAR(50) | 是 | | 资产类型：FULL_EXPLANATION / BEGINNER_EXPLANATION / STEP_BY_STEP / WRONG_OPTION_ANALYSIS / COMMON_MISTAKES / VARIANT |
| content | TEXT | 是 | | AI 生成的 Markdown 内容 |
| model | VARCHAR(100) | 否 | | 生成内容的模型名称 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-正常 1-已删除 |

**索引**：
- 唯一索引：`uk_question_asset_type` (`question_id`, `asset_type`, `deleted`) — 同一题同一类型只保留一条缓存
- 普通索引：`idx_question_id` (`question_id`)

```sql
CREATE TABLE `question_ai_asset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '资产ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `asset_type` VARCHAR(50) NOT NULL COMMENT '资产类型',
  `content` TEXT NOT NULL COMMENT 'AI生成内容（Markdown）',
  `model` VARCHAR(100) DEFAULT NULL COMMENT '模型名称',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_asset_type` (`question_id`, `asset_type`, `deleted`),
  KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题目AI学习资产表';
```

### 3.15 AI 学习资产反馈表 (ai_asset_feedback) - Phase 13

存储用户对 AI 生成的学习资产的质量反馈（有帮助/无帮助），同一用户对同一题同一资产类型只能反馈一次。

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|:----:|--------|------|
| id | BIGINT | 是 | 自增主键 | 反馈ID |
| question_id | BIGINT | 是 | | 题目ID |
| asset_type | VARCHAR(50) | 是 | | 资产类型 |
| user_id | BIGINT | 是 | | 用户ID |
| helpful | TINYINT | 是 | | 是否有帮助：1-有帮助 0-无帮助 |
| comment | VARCHAR(500) | 否 | | 用户补充反馈文字（可选） |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |

**索引**：
- 唯一索引：`uk_asset_user` (`question_id`, `asset_type`, `user_id`) — 同一用户对同一题同一类型只有一条反馈
- 普通索引：`idx_question_asset` (`question_id`, `asset_type`)

**建表 SQL**：
```sql
CREATE TABLE `ai_asset_feedback` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `asset_type` VARCHAR(50) NOT NULL COMMENT '资产类型',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `helpful` TINYINT NOT NULL COMMENT '是否有帮助：1-有帮助 0-无帮助',
  `comment` VARCHAR(500) DEFAULT NULL COMMENT '用户补充反馈文字（可选）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_user` (`question_id`, `asset_type`, `user_id`),
  KEY `idx_question_asset` (`question_id`, `asset_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 学习资产质量反馈表';
```

---

### 3.16 题目投稿表 (question_submission)

`question_submission` 是 Phase 16 题目投稿中心的审核流转表，用于保存用户提交但尚未成为正式题库题目的内容。管理员审核通过后，可将投稿入库为正式 `question`，并通过 `imported_question_id` 记录入库后的题目 ID。

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|:----:|--------|------|
| id | BIGINT | 是 | 自增主键 | 投稿ID |
| user_id | BIGINT | 是 | | 投稿用户ID |
| content | TEXT | 是 | | 题干内容（支持 Markdown） |
| question_type | VARCHAR(20) | 是 | | 题型：SINGLE_CHOICE / MULTIPLE_CHOICE / TRUE_FALSE / FILL_BLANK / SHORT_ANSWER |
| course_id | BIGINT | 是 | | 所属课程ID |
| difficulty | TINYINT | 是 | 3 | 难度等级：1-5 |
| analysis | TEXT | 否 | | 题目解析 |
| options_json | TEXT | 否 | | 选择题/判断题选项 JSON |
| correct_answer | VARCHAR(2000) | 否 | | 判断题、填空题、简答题参考答案 |
| knowledge_point_ids | VARCHAR(500) | 否 | | 关联知识点 ID，逗号分隔 |
| tags | VARCHAR(500) | 否 | | 标签，逗号分隔 |
| source | VARCHAR(200) | 否 | | 题目来源 |
| status | TINYINT | 是 | 0 | 0-待审核 1-已通过 2-已拒绝 3-已入库 |
| review_comment | VARCHAR(1000) | 否 | | 审核意见 |
| reviewed_by | BIGINT | 否 | | 审核人ID |
| reviewed_time | DATETIME | 否 | | 审核时间 |
| imported_question_id | BIGINT | 否 | | 入库后的正式题目ID |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 投稿时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除 |

**索引**：
- INDEX `idx_user_id` ON `user_id`
- INDEX `idx_status` ON `status`
- INDEX `idx_course_id` ON `course_id`
- INDEX `idx_create_time` ON `create_time`

**入库规则**：
- 选择题入库时按 `options_json` 创建正式 `question_option`。
- 判断题入库时会规范化为“正确/错误”两个正式选项，并标记唯一正确项。
- 填空题和简答题入库时会将 `correct_answer` 写入 `question_option.content`，`option_label=ANSWER`，保持刷题判分统一从正式选项表读取正确答案。

---

### 3.17 题目来源与复审（question 扩展、question_review_record）

Flyway V8 为正式题目补充来源治理字段，并新增只追加的复审审计表。

`question` 新增字段：

| 字段名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| source_type | VARCHAR(30) | MANUAL | 来源：MANUAL / SUBMISSION / EXCEL_IMPORT / MARKDOWN_IMPORT / AI_GENERATED |
| source_reference | VARCHAR(500) | NULL | 来源引用，如投稿 ID 或导入批次 ID |
| last_review_time | DATETIME | NULL | 最近复审时间 |
| next_review_time | DATETIME | NULL | 下次复审时间 |
| review_rounds | INT | 0 | 累计复审轮次 |

`question_review_record` 用于保留复审动作快照：`question_id`、`reviewer_id`、复审类型、动作、题干/难度变更前后值、意见与 `create_time`。该表是审计记录，只有创建时间，不包含逻辑删除与更新时间。

---

### 3.18 题目纠错反馈表 (question_correction_report)

Flyway V15 创建，用于记录用户对正式题目的纠错反馈，以及管理员处理结果。该表只做反馈和处理留痕，不自动修改正式题目。

| 字段名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| id | BIGINT | 自增主键 | 纠错记录 ID |
| question_id | BIGINT | | 被反馈的题目 ID |
| reporter_id | BIGINT | | 提交反馈的用户 ID |
| report_type | VARCHAR(30) | | CONTENT / ANSWER / ANALYSIS / KNOWLEDGE_POINT / OTHER |
| description | VARCHAR(1000) | | 问题描述 |
| status | VARCHAR(20) | OPEN | OPEN / RESOLVED / REJECTED |
| handler_id | BIGINT | NULL | 处理管理员 ID |
| handler_comment | VARCHAR(1000) | NULL | 处理说明 |
| handled_time | DATETIME | NULL | 处理时间 |
| create_time | DATETIME | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| deleted | TINYINT | 0 | 逻辑删除 |

索引：`idx_question_status(question_id,status)`、`idx_reporter_time(reporter_id,create_time)`、`idx_status_time(status,create_time)`、`idx_handler_id(handler_id)`。

---

### 3.19 题目版本记录表 (question_version)

Flyway V16 创建，用于记录正式题目的创建、编辑、删除和复审变更快照。该表只做审计追踪，不提供自动回滚，也不替代题目编辑或复审流程。

| 字段名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| id | BIGINT | 自增主键 | 版本记录 ID |
| question_id | BIGINT | | 题目 ID |
| version_no | INT | | 同一题目内从 1 递增的版本号 |
| change_type | VARCHAR(30) | | CREATE / UPDATE / DELETE / REVIEW_APPROVE / REVIEW_REVISE / REVIEW_REJECT |
| operator_id | BIGINT | NULL | 操作人 ID |
| change_summary | VARCHAR(500) | NULL | 变更摘要 |
| snapshot_before | JSON | NULL | 变更前快照 |
| snapshot_after | JSON | NULL | 变更后快照 |
| create_time | DATETIME | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| deleted | TINYINT | 0 | 逻辑删除 |

索引与约束：唯一约束 `uk_question_version(question_id, version_no)`；索引 `idx_question_id`、`idx_operator_id`、`idx_change_type`、`idx_create_time`。

---

### 3.20 间隔重复复习计划表 (question_review_schedule)

Flyway V9 创建的用户级 SM-2 复习调度表；同一用户和题目只有一条有效计划。

| 字段名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| id | BIGINT | 自增主键 | 计划 ID |
| user_id | BIGINT | | 用户 ID |
| question_id | BIGINT | | 题目 ID |
| ease_factor | DECIMAL(4,2) | 2.50 | SM-2 简易因子，最低 1.30 |
| interval_days | INT | 0 | 当前复习间隔（天） |
| repetitions | INT | 0 | 连续正确次数 |
| next_review_date | DATE | | 下次复习日期 |
| last_review_date | DATE | NULL | 上次复习日期 |
| last_quality | INT | NULL | 上次回忆质量（0-5） |
| total_reviews | INT | 0 | 总复习次数 |
| create_time / update_time | DATETIME | CURRENT_TIMESTAMP | 创建与更新时间 |
| deleted | INT | 0 | 逻辑删除标识 |

**索引与约束**：唯一约束 `uk_user_question(user_id, question_id)`；索引 `idx_user_next_review(user_id, next_review_date, deleted)` 和 `idx_question_id(question_id)`。

---

### 3.21 AI 学习资产查看记录表 (ai_asset_view)

Flyway V17 创建，用于记录用户实际看到某道题某类 AI 学习资产的行为。为控制事件量，同一用户、题目、资产类型在同一天只保留一行，并原子累加 `view_count`；该表可结合 `practice_record` 和 `question_knowledge_point` 观察后续同题作答及共享知识点跨题作答的相关性，不作为 AI 调用次数或配额依据。

| 字段名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| id | BIGINT | 自增主键 | 查看记录 ID |
| user_id | BIGINT | | 用户 ID |
| question_id | BIGINT | | 题目 ID |
| asset_type | VARCHAR(50) | | AI 学习资产类型 |
| view_date | DATE | | 查看日期 |
| view_count | INT | 1 | 当日查看次数 |
| first_view_time | DATETIME | CURRENT_TIMESTAMP | 当日首次查看时间 |
| last_view_time | DATETIME | CURRENT_TIMESTAMP | 当日最近查看时间 |
| create_time | DATETIME | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

索引与约束：唯一约束 `uk_user_question_asset_date(user_id, question_id, asset_type, view_date)`；索引 `idx_view_date(view_date)`、`idx_user_question_time(user_id, question_id, first_view_time)`。

---

### 3.22 AI 变式训练记录表 (ai_variant_training)

Flyway V18 创建，用于区分“看到了变式题”和“用户显式确认完成训练”。变式题进入浏览器视口时创建 `STARTED` 记录，用户独立作答并核对解析后点击确认才更新为 `COMPLETED`；不从 AI 生成次数或调用日志推断完成。

| 字段名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| id | BIGINT | 自增主键 | 训练记录 ID |
| user_id | BIGINT | | 用户 ID |
| question_id | BIGINT | | 原题 ID |
| asset_id | BIGINT | | 当前变式题缓存资产 ID |
| status | VARCHAR(20) | STARTED | `STARTED` / `COMPLETED` |
| started_time | DATETIME | CURRENT_TIMESTAMP | 首次开始时间 |
| last_view_time | DATETIME | CURRENT_TIMESTAMP | 最近查看时间 |
| completed_time | DATETIME | NULL | 用户显式确认完成时间 |
| create_time | DATETIME | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

索引与约束：唯一约束 `uk_user_asset(user_id, asset_id)`，确保同一缓存资产版本不会因重复切换标签产生多条训练；索引 `idx_question_status(question_id, status)`、`idx_started_time(started_time)`、`idx_completed_time(completed_time)`。

---

## 四、表关系说明

### 4.1 外键关系（逻辑外键，不建物理外键）

| 关系 | 父表 | 父表字段 | 子表 | 子表字段 |
|------|------|----------|------|----------|
| 课程→知识点 | course | id | knowledge_point | course_id |
| 课程→题目 | course | id | question | course_id |
| 题目→选项 | question | id | question_option | question_id |
| 题目→知识点 | question | id | question_knowledge_point | question_id |
| 知识点→题目 | knowledge_point | id | question_knowledge_point | knowledge_point_id |
| 用户→刷题记录 | user | id | practice_record | user_id |
| 用户→错题 | user | id | wrong_question | user_id |
| 用户→题目投稿 | user | id | question_submission | user_id |
| 用户→投稿审核 | user | id | question_submission | reviewed_by |
| 用户→复习计划 | user | id | question_review_schedule | user_id |
| 用户→题目复审 | user | id | question_review_record | reviewer_id |
| 用户→题目纠错 | user | id | question_correction_report | reporter_id |
| 用户→纠错处理 | user | id | question_correction_report | handler_id |
| 用户→题目版本操作 | user | id | question_version | operator_id |
| 用户→AI资产查看 | user | id | ai_asset_view | user_id |
| 用户→变式训练 | user | id | ai_variant_training | user_id |
| 题目→刷题记录 | question | id | practice_record | question_id |
| 题目→错题 | question | id | wrong_question | question_id |
| 题目→AI学习资产 | question | id | question_ai_asset | question_id |
| AI学习资产→反馈 | question_ai_asset | question_id, asset_type | ai_asset_feedback | question_id, asset_type |
| AI学习资产→查看 | question_ai_asset | question_id, asset_type | ai_asset_view | question_id, asset_type |
| AI学习资产→变式训练 | question_ai_asset | id | ai_variant_training | asset_id |
| 课程→题目投稿 | course | id | question_submission | course_id |
| 正式题目→投稿入库结果 | question | id | question_submission | imported_question_id |
| 题目→复习计划 | question | id | question_review_schedule | question_id |
| 题目→复审记录 | question | id | question_review_record | question_id |
| 题目→纠错反馈 | question | id | question_correction_report | question_id |
| 题目→版本记录 | question | id | question_version | question_id |
| 试卷→考试记录 | exam_paper | id | exam_record | exam_paper_id |
| 考试记录→答题 | exam_record | id | exam_answer | exam_record_id |
| 试卷→题目关联 | exam_paper | id | exam_question | exam_paper_id |

> **注意**：不使用物理外键约束，通过应用层维护数据一致性。这是 MyBatis-Plus 项目的常见实践。

### 4.2 知识点自引用关系

```
knowledge_point 表中 parent_id 指向同表的 id：
- parent_id = 0  → 顶级知识点
- parent_id = X  → 属于知识点 X 的子知识点
```

---

## 五、初始测试数据

### 5.1 管理员账号
```sql
INSERT INTO `user` (`username`, `password`, `nickname`, `role`, `status`) VALUES
('admin', '$2a$10$ew3eqgztO50uM0K2V73iteEar40Byftspgl6u4qcWYgcobahZMixe', '管理员', 'ADMIN', 1);
-- 密码: admin123 (BCrypt加密)
```

### 5.2 测试用户
```sql
INSERT INTO `user` (`username`, `password`, `nickname`, `role`, `status`) VALUES
('testuser', '$2a$10$kjBlBHk3g4kw2L2wajkoFOSaLPJTTdU3ZEjLWHLhMlhRIIllXz0x6', '测试用户', 'USER', 1);
-- 密码: test123 (BCrypt加密)
```

### 5.3 示例课程
```sql
INSERT INTO `course` (`name`, `description`, `sort_order`) VALUES
('Java 基础', 'Java 编程语言基础知识，包括语法、面向对象、异常处理等', 1),
('数据结构与算法', '常见数据结构和算法的学习与练习', 2),
('数据库原理', '关系型数据库原理、SQL 语法、索引优化等', 3),
('计算机网络', 'TCP/IP、HTTP、网络协议等计算机网络基础知识', 4),
('操作系统', '进程管理、内存管理、文件系统等操作系统核心知识', 5);
```

### 5.4 演示学习数据

数据库由 Flyway 管理，迁移脚本位于 `backend/src/main/resources/db/migration`。`V1__baseline.sql` 使用 `utf8mb4` 客户端字符集，并为 Java 基础课程写入演示数据；后续结构变化必须新增版本迁移，不再直接修改已发布迁移。

---

## 六、数据量预估与优化建议

### 6.1 数据量预估

| 表 | 预估数据量 | 说明 |
|----|-----------|------|
| user | 1,000 | 测试规模 |
| course | 10-50 | 课程数量有限 |
| knowledge_point | 100-500 | 每课程 10-20 个知识点 |
| question | 1,000-10,000 | 核心数据 |
| question_option | 4,000-40,000 | 每题 4 个选项 |
| practice_record | 10,000-100,000 | 用户刷题记录，增长最快 |
| wrong_question | 1,000-10,000 | 错题记录 |
| exam_paper | 50-200 | 试卷数量有限 |
| exam_record | 1,000-10,000 | 考试记录 |
| exam_answer | 10,000-100,000 | 考试答题详情 |
| ai_asset_view | 10,000-100,000 | 按用户、题目、资产类型和日期聚合的查看记录 |
| ai_variant_training | 5,000-50,000 | 按用户和变式题缓存资产版本去重的训练记录 |

### 6.2 优化建议

**当前阶段（无需优化）**：
- 数据量小，MySQL 默认配置足够
- MyBatis-Plus 自动生成的基础 SQL 性能良好

**后期优化方向**：
1. **索引优化**：根据实际查询模式添加组合索引
2. **分表**：practice_record 和 exam_answer 如果数据量过大，可以按用户ID分表
3. **缓存**：Redis 缓存热点数据（课程列表、题目详情）
4. **读写分离**：主从复制，读操作走从库
5. **慢查询监控**：开启 MySQL 慢查询日志

---

## 七、后续扩展建议

1. **学习计划表**：存储 AI 生成的复习计划
2. **用户收藏表**：收藏题目/知识点
3. **讨论区表**：题目讨论/问答
4. **通知表**：系统通知/提醒
5. **操作日志表**：管理端操作审计
6. **用户学习进度表**：按知识点的学习进度跟踪
