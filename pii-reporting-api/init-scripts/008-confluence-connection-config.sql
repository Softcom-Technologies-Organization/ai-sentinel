-- Create Confluence Connection Config Table (Singleton Configuration)
-- This table stores connection settings for the Confluence integration
-- Single row table with id = 1

CREATE TABLE IF NOT EXISTS confluence_connection_config
(
    id                  INTEGER PRIMARY KEY,
    base_url            TEXT                     NOT NULL DEFAULT '',
    username            TEXT                     NOT NULL DEFAULT '',
    api_token_encrypted TEXT                     NOT NULL DEFAULT '',
    connect_timeout     INTEGER                  NOT NULL DEFAULT 30000,
    read_timeout        INTEGER                  NOT NULL DEFAULT 60000,
    max_retries         INTEGER                  NOT NULL DEFAULT 3,
    pages_limit         INTEGER                  NOT NULL DEFAULT 50,
    max_pages           INTEGER                  NOT NULL DEFAULT 100,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(255)                      DEFAULT 'system',
    CONSTRAINT check_single_row CHECK (id = 1)
);

-- Insert default configuration row
INSERT INTO confluence_connection_config (id) VALUES (1) ON CONFLICT DO NOTHING;
