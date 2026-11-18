ALTER TABLE pii_detection_config
    ADD COLUMN IF NOT EXISTS judge_provider VARCHAR(16),
    ADD COLUMN IF NOT EXISTS judge_host VARCHAR(255),
    ADD COLUMN IF NOT EXISTS judge_port INTEGER;
