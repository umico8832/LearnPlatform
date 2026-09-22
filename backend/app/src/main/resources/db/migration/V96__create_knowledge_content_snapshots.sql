CREATE TABLE knowledge_content_bundle (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_key VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    bundle_version VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    manifest_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_revision VARCHAR(100) NOT NULL,
    source_quality_status VARCHAR(30) NOT NULL,
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    chunk_count INT NOT NULL,
    imported_by BIGINT NOT NULL,
    imported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_knowledge_bundle_version (course_key, bundle_version),
    CONSTRAINT ck_knowledge_bundle_review CHECK (review_status IN ('PENDING', 'REVIEWED', 'WITHDRAWN')),
    CONSTRAINT ck_knowledge_bundle_count CHECK (chunk_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE knowledge_content_chunk (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bundle_id BIGINT NOT NULL,
    chunk_key VARCHAR(150) COLLATE utf8mb4_bin NOT NULL,
    concept_key VARCHAR(150) COLLATE utf8mb4_bin NOT NULL,
    title VARCHAR(255) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    metadata_json JSON NOT NULL,
    source_quality_status VARCHAR(30) NOT NULL,
    UNIQUE KEY uk_knowledge_chunk_version (bundle_id, chunk_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
