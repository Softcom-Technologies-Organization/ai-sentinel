-- Optional helper script to provision the minimal table for JDBC checkpoints
-- Business Rule: BR-SCAN-002 - Includes optimistic locking version for preventing concurrent update conflicts
CREATE TABLE IF NOT EXISTS scan_checkpoints (
  scan_id TEXT NOT NULL,
  space_key TEXT NOT NULL,
  last_processed_page_id TEXT NULL,
  last_processed_attachment_name TEXT NULL,
  status TEXT NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  version BIGINT DEFAULT 0 NOT NULL,
  progress_percentage DOUBLE PRECISION,
  PRIMARY KEY (scan_id, space_key)
);

-- Comment explaining the purpose of the version column
COMMENT ON COLUMN scan_checkpoints.version IS 'Optimistic locking version for preventing concurrent update conflicts. Automatically incremented by JPA on each update. Ensures COMPLETED/FAILED states remain immutable.';

-- Comment explaining the purpose of the progress_percentage column
COMMENT ON COLUMN scan_checkpoints.progress_percentage IS 'Percentage of scan completion (0.0 to 100.0). Persisted from ScanResult.analysisProgressPercentage during scan execution.';
