CREATE TABLE `user_identity` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `provider` VARCHAR(20) NOT NULL,
  `provider_subject` VARCHAR(255) NOT NULL,
  `email_snapshot` VARCHAR(254) DEFAULT NULL,
  `display_name` VARCHAR(255) DEFAULT NULL,
  `avatar_url` VARCHAR(1024) DEFAULT NULL,
  `last_login_at` DATETIME NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_identity_provider_subject` (`provider`, `provider_subject`),
  UNIQUE KEY `uk_user_identity_user_provider` (`user_id`, `provider`),
  KEY `idx_user_identity_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户第三方登录身份';

CREATE TABLE `oauth_login_ticket` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `token_hash` CHAR(64) NOT NULL,
  `expires_at` DATETIME NOT NULL,
  `used_at` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_oauth_login_ticket_hash` (`token_hash`),
  KEY `idx_oauth_login_ticket_user` (`user_id`, `create_time`),
  KEY `idx_oauth_login_ticket_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='第三方登录一次性票据';
