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
