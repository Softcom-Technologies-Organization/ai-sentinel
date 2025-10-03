-- Optional helper script to provision the minimal table for JDBC checkpoints
CREATE TABLE IF NOT EXISTS scan_checkpoints (
  scan_id TEXT NOT NULL,
  space_key TEXT NOT NULL,
  last_processed_page_id TEXT NULL,
  last_processed_attachment_name TEXT NULL,
  status TEXT NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (scan_id, space_key)
);
