-- 学习效果闭环：记录用户实际查看 AI 学习资产的行为
-- 按用户、题目、资产类型和日期聚合，避免同一会话重复切换产生大量明细事件

CREATE TABLE `ai_asset_view` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `asset_type` VARCHAR(50) NOT NULL COMMENT '资产类型',
  `view_date` DATE NOT NULL COMMENT '查看日期',
  `view_count` INT NOT NULL DEFAULT 1 COMMENT '当日查看次数',
  `first_view_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '当日首次查看时间',
  `last_view_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '当日最近查看时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_question_asset_date` (`user_id`, `question_id`, `asset_type`, `view_date`),
  KEY `idx_view_date` (`view_date`),
  KEY `idx_user_question_time` (`user_id`, `question_id`, `first_view_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 学习资产查看记录';
