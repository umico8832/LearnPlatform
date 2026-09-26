ALTER TABLE tutor_agent_run
    ADD COLUMN execution_key CHAR(36) DEFAULT NULL,
    ADD COLUMN lease_until DATETIME DEFAULT NULL;
