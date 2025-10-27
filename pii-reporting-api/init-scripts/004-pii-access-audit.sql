-- PII Access Audit Table
-- Tracks all access to decrypted PII data for GDPR/nLPD compliance
-- Purpose: GDPR Art. 30, 32 + nLPD Art. 6, 8, 12, 24

CREATE TABLE IF NOT EXISTS pii_access_audit (
    id BIGSERIAL PRIMARY KEY,
    scan_id VARCHAR(100) NOT NULL,
    accessed_at TIMESTAMP NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    pii_entities_count INTEGER,
    retention_until TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_pii_access_audit_scan 
        FOREIGN KEY (scan_id) 
        REFERENCES scan_events(scan_id) 
        ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_pii_access_audit_scan_id 
    ON pii_access_audit(scan_id);

CREATE INDEX IF NOT EXISTS idx_pii_access_audit_accessed_at 
    ON pii_access_audit(accessed_at);

CREATE INDEX IF NOT EXISTS idx_pii_access_audit_retention 
    ON pii_access_audit(retention_until);

-- Comments for documentation
COMMENT ON TABLE pii_access_audit IS 
    'Audit log for PII access (GDPR/nLPD compliance). Tracks who accessed decrypted PII data, when, and for what purpose.';

COMMENT ON COLUMN pii_access_audit.purpose IS 
    'Access purpose: ADMIN_REVIEW, COMPLIANCE_REPORTING, TECHNICAL_SUPPORT, DATA_EXPORT';

COMMENT ON COLUMN pii_access_audit.retention_until IS 
    'Retention deadline (default: 2 years from access for nLPD compliance)';

COMMENT ON COLUMN pii_access_audit.pii_entities_count IS 
    'Number of PII entities accessed in this operation';
