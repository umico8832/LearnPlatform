-- Bounded expiry batches seek by create_time and must not scan the full replay snapshot table.
CREATE INDEX idx_http_idempotency_create_time ON http_idempotency_record (create_time);
