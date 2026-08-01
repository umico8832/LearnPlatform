ALTER TABLE `user`
  ADD COLUMN `email` VARCHAR(254) NULL COMMENT '登录邮箱' AFTER `username`,
  ADD COLUMN `email_verified_at` DATETIME NULL COMMENT '邮箱验证时间' AFTER `email`,
  ADD COLUMN `auth_version` INT NOT NULL DEFAULT 0 COMMENT '认证版本，修改密码后递增' AFTER `status`,
  ADD UNIQUE KEY `uk_user_email` (`email`);

CREATE TABLE `email_verification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `email` VARCHAR(254) NOT NULL,
  `purpose` VARCHAR(20) NOT NULL,
  `code_hash` CHAR(64) NOT NULL,
  `ticket_hash` CHAR(64) DEFAULT NULL,
  `attempt_count` INT NOT NULL DEFAULT 0,
  `expires_at` DATETIME NOT NULL,
  `verified_at` DATETIME DEFAULT NULL,
  `used_at` DATETIME DEFAULT NULL,
  `ip_address` VARCHAR(64) DEFAULT NULL,
  `user_agent` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_email_verification_lookup` (`email`, `purpose`, `create_time`),
  KEY `idx_email_verification_ticket` (`ticket_hash`),
  KEY `idx_email_verification_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='邮箱验证记录';

CREATE TABLE `password_reset_token` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `token_hash` CHAR(64) NOT NULL,
  `expires_at` DATETIME NOT NULL,
  `used_at` DATETIME DEFAULT NULL,
  `ip_address` VARCHAR(64) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_password_reset_token_hash` (`token_hash`),
  KEY `idx_password_reset_user` (`user_id`, `create_time`),
  KEY `idx_password_reset_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='密码重置令牌';
