-- 变式训练真实完成事件：进入变式题内容记为开始，用户显式确认后记为完成
-- 每个用户对每个缓存资产版本只保留一条训练记录，避免重复切换标签夸大完成量

CREATE TABLE `ai_variant_training` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `question_id` BIGINT NOT NULL COMMENT '原题ID',
  `asset_id` BIGINT NOT NULL COMMENT '变式题学习资产ID',
  `status` VARCHAR(20) NOT NULL DEFAULT 'STARTED' COMMENT '训练状态：STARTED-已开始,COMPLETED-已完成',
  `started_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次开始时间',
  `last_view_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近查看时间',
  `completed_time` DATETIME NULL COMMENT '用户显式确认完成时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_asset` (`user_id`, `asset_id`),
  KEY `idx_question_status` (`question_id`, `status`),
  KEY `idx_started_time` (`started_time`),
  KEY `idx_completed_time` (`completed_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 变式训练记录';
