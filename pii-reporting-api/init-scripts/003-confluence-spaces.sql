-- Table for caching Confluence spaces to improve UI performance
-- Stores only fields displayed in UI dashboard: id, key, name, url, description
-- Additional metadata stored in flexible JSONB column for future extensibility
CREATE TABLE IF NOT EXISTS confluence_spaces (
  id TEXT NOT NULL PRIMARY KEY,
  key TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  url TEXT,
  description TEXT,
  cache_timestamp TIMESTAMP NOT NULL,
  last_updated TIMESTAMP NOT NULL
);

-- Index for efficient staleness queries in background refresh
CREATE INDEX IF NOT EXISTS idx_confluence_spaces_last_updated ON confluence_spaces(last_updated);
