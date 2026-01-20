-- Create PII Type Configuration Table
-- This table stores per-PII-type configuration (enable/disable and thresholds)
-- for each detector (GLiNER, Presidio, Regex)

CREATE TABLE IF NOT EXISTS pii_type_config
(
    id            BIGSERIAL PRIMARY KEY,
    pii_type      VARCHAR(100)             NOT NULL,
    detector      VARCHAR(50)              NOT NULL,
    enabled       BOOLEAN                  NOT NULL DEFAULT true,
    threshold     DOUBLE PRECISION         NOT NULL,
    category      VARCHAR(100),
    country_code  VARCHAR(10),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(255)             NOT NULL DEFAULT 'system',
    CONSTRAINT unique_type_detector UNIQUE (pii_type, detector),
    CONSTRAINT check_threshold CHECK (threshold >= 0.0 AND threshold <= 1.0),
    CONSTRAINT check_detector CHECK (detector IN ('GLINER', 'PRESIDIO', 'REGEX'))
);

CREATE INDEX IF NOT EXISTS idx_pii_type_config_detector ON pii_type_config (detector);