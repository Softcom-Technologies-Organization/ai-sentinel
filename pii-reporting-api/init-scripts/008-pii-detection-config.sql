-- Create PII Detection Config Table (Singleton Configuration)
-- This table stores global configuration for PII detectors (GLiNER, Presidio, Regex)
-- Single row table with id = 1

CREATE TABLE IF NOT EXISTS pii_detection_config
(
    id                INTEGER PRIMARY KEY,
    gliner_enabled    BOOLEAN                  NOT NULL DEFAULT true,
    presidio_enabled  BOOLEAN                  NOT NULL DEFAULT true,
    regex_enabled     BOOLEAN                  NOT NULL DEFAULT true,
    default_threshold DECIMAL(3, 2)            NOT NULL DEFAULT 0.80,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(255)                      DEFAULT 'system',
    CONSTRAINT check_default_threshold CHECK (default_threshold >= 0.0 AND default_threshold <= 1.0),
    CONSTRAINT check_single_row CHECK (id = 1)
);

-- Insert default configuration (single row with id=1)
INSERT INTO pii_detection_config (id, gliner_enabled, presidio_enabled, regex_enabled, default_threshold, updated_at, updated_by)
VALUES (1, true, true, true, 0.80, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (id) DO NOTHING;

-- Comment on table and columns for documentation
COMMENT ON TABLE pii_detection_config IS 'Global PII detection configuration (singleton table with id=1)';
COMMENT ON COLUMN pii_detection_config.id IS 'Primary key (always 1 - singleton pattern)';
COMMENT ON COLUMN pii_detection_config.gliner_enabled IS 'Enable/disable GLiNER detector';
COMMENT ON COLUMN pii_detection_config.presidio_enabled IS 'Enable/disable Presidio detector';
COMMENT ON COLUMN pii_detection_config.regex_enabled IS 'Enable/disable Regex detector';
COMMENT ON COLUMN pii_detection_config.default_threshold IS 'Default confidence threshold (0.0-1.0)';
COMMENT ON COLUMN pii_detection_config.updated_at IS 'Last update timestamp';
COMMENT ON COLUMN pii_detection_config.updated_by IS 'User who last updated the config';
