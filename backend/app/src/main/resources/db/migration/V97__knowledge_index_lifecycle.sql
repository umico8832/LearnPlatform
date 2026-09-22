ALTER TABLE knowledge_content_bundle
    ADD COLUMN reviewed_by BIGINT DEFAULT NULL AFTER review_status,
    ADD COLUMN reviewed_at DATETIME DEFAULT NULL AFTER reviewed_by,
    ADD COLUMN review_note VARCHAR(1000) DEFAULT NULL AFTER reviewed_at;

CREATE TABLE knowledge_content_index (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bundle_id BIGINT NOT NULL,
    index_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    model VARCHAR(100) NOT NULL,
    dimensions INT NOT NULL,
    collection_name VARCHAR(200) NOT NULL,
    vector_endpoint_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) NOT NULL,
    run_key CHAR(36) DEFAULT NULL,
    lease_until DATETIME DEFAULT NULL,
    indexed_count INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_knowledge_index_bundle_key (bundle_id, index_key),
    KEY idx_knowledge_index_ready (bundle_id, index_key, status),
    CONSTRAINT ck_knowledge_index_status CHECK (status IN ('INDEXING', 'READY', 'FAILED', 'DELETED', 'PURGED')),
    CONSTRAINT ck_knowledge_index_dimensions CHECK (dimensions > 0),
    CONSTRAINT ck_knowledge_index_count CHECK (indexed_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
