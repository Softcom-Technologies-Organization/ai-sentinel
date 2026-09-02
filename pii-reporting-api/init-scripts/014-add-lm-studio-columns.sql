-- ============================================================================
-- LM Studio endpoint configuration — additive, idempotent migration.
-- ============================================================================
-- The schema is primarily managed by Hibernate ddl-auto (these columns are
-- declared on PiiDetectionConfigEntity). This script keeps Docker-bootstrapped
-- environments consistent and is safe to run multiple times.
--
-- lm_studio_host / lm_studio_port locate the OpenAI-compatible LM Studio
-- endpoint that serves the Ministral-PII model. The Python detector reads them
-- at scan start (fetch_config_from_db) and builds http://host:port/v1.
-- Defaults mirror the detector's DEFAULT_BASE_URL (http://localhost:1234/v1).
-- ============================================================================

ALTER TABLE pii_detection_config
    ADD COLUMN IF NOT EXISTS lm_studio_host VARCHAR(255) NOT NULL DEFAULT 'localhost';
ALTER TABLE pii_detection_config
    ADD COLUMN IF NOT EXISTS lm_studio_port INTEGER NOT NULL DEFAULT 1234;
ALTER TABLE pii_detection_config
    ADD COLUMN IF NOT EXISTS lm_studio_model VARCHAR(255);

COMMENT ON COLUMN pii_detection_config.lm_studio_host IS
    'Host of the OpenAI-compatible LM Studio endpoint serving the Ministral-PII model. Default: localhost.';
COMMENT ON COLUMN pii_detection_config.lm_studio_port IS
    'Port of the OpenAI-compatible LM Studio endpoint serving the Ministral-PII model. Default: 1234.';
COMMENT ON COLUMN pii_detection_config.lm_studio_model IS
    'LM Studio identifier of the Ministral-PII model to prompt (a quantization of the family, e.g. ministral-3b-pii-preview@q4_k_m). NULL: detector service default.';

ALTER TABLE pii_detection_config DROP CONSTRAINT IF EXISTS chk_lm_studio_port_range;
ALTER TABLE pii_detection_config
    ADD CONSTRAINT chk_lm_studio_port_range
    CHECK (lm_studio_port BETWEEN 1 AND 65535);
