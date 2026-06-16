-- Phase 13: AI 题目学习资产缓存表
-- 存储 AI 生成的结构化学习资产，避免重复调用 AI

CREATE TABLE `question_ai_asset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `asset_type` VARCHAR(50) NOT NULL COMMENT '资产类型：FULL_EXPLANATION-标准解析,BEGINNER_EXPLANATION-小白版,STEP_BY_STEP-步骤拆解,WRONG_OPTION_ANALYSIS-错误选项分析,COMMON_MISTAKES-常见误区,VARIANT-变式题',
  `content` TEXT NOT NULL COMMENT 'AI 生成的 Markdown 内容',
  `model` VARCHAR(100) DEFAULT NULL COMMENT 'AI 模型名称',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_asset_type` (`question_id`, `asset_type`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 题目学习资产缓存表';