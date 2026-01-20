-- Table for caching Confluence spaces to improve UI performance
-- Stores only fields displayed in UI dashboard: id, key, name, url, description
CREATE TABLE IF NOT EXISTS confluence_spaces (
  id TEXT NOT NULL PRIMARY KEY,
  space_key TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  url TEXT,
  description TEXT,
  cache_timestamp TIMESTAMP NOT NULL,
  last_synced_at TIMESTAMP NOT NULL,
  confluence_last_modified_at TIMESTAMP
);

-- Index for efficient staleness queries in background refresh
CREATE INDEX IF NOT EXISTS idx_confluence_spaces_last_synced_at ON confluence_spaces(last_synced_at);
