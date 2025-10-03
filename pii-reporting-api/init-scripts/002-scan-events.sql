-- Event sourcing tables for scan runs and events (JSONB payload)
CREATE TABLE IF NOT EXISTS scan_runs (
  scan_id TEXT PRIMARY KEY,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  status TEXT NOT NULL,
  initiated_by TEXT NULL,
  params JSONB NULL,
  note TEXT NULL
);
CREATE INDEX IF NOT EXISTS idx_scan_runs_updated_at ON scan_runs(updated_at DESC);

CREATE TABLE IF NOT EXISTS scan_events (
  scan_id TEXT NOT NULL,
  event_seq BIGINT NOT NULL,
  space_key TEXT NULL,
  event_type TEXT NOT NULL,
  ts TIMESTAMPTZ NOT NULL DEFAULT now(),
  page_id TEXT NULL,
  page_title TEXT NULL,
  attachment_name TEXT NULL,
  attachment_type TEXT NULL,
  payload JSONB NOT NULL,
  PRIMARY KEY (scan_id, event_seq)
);
CREATE INDEX IF NOT EXISTS idx_scan_events_scan_ts ON scan_events(scan_id, ts DESC);
CREATE INDEX IF NOT EXISTS idx_scan_events_space ON scan_events(space_key);
CREATE INDEX IF NOT EXISTS idx_scan_events_page ON scan_events(page_id);
CREATE INDEX IF NOT EXISTS idx_scan_events_payload_gin ON scan_events USING GIN (payload);
