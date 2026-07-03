CREATE TABLE `question_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '版本记录ID',
  `question_id` BIGINT NOT NULL COMMENT '题目ID',
  `version_no` INT NOT NULL COMMENT '题目版本号，从1递增',
  `change_type` VARCHAR(30) NOT NULL COMMENT '变更类型：CREATE/UPDATE/DELETE/REVIEW_APPROVE/REVIEW_REVISE/REVIEW_REJECT',
  `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
  `change_summary` VARCHAR(500) DEFAULT NULL COMMENT '变更摘要',
  `snapshot_before` JSON DEFAULT NULL COMMENT '变更前快照',
  `snapshot_after` JSON DEFAULT NULL COMMENT '变更后快照',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_version` (`question_id`, `version_no`),
  KEY `idx_question_id` (`question_id`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_change_type` (`change_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题目版本记录表';
