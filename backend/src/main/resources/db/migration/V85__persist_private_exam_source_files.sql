ALTER TABLE user_exam_source
    ADD COLUMN source_media_type VARCHAR(127) NULL COMMENT '原始文件媒体类型' AFTER original_content,
    ADD COLUMN source_size BIGINT UNSIGNED NULL COMMENT '原始文件字节数' AFTER source_media_type,
    ADD COLUMN source_file MEDIUMBLOB NULL COMMENT '仅所有者可访问的原始文件' AFTER source_size;
