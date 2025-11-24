-- Idempotent script to create the PostgreSQL database used by AI-Sentinel.
-- It uses a psql meta-command (\gexec) to execute CREATE DATABASE only when it does not exist.
-- Notes:
-- - CREATE DATABASE cannot run inside a transaction block; this script assumes psql default autocommit.
-- - When using the official postgres Docker image with POSTGRES_DB=sentinelle, the DB will already
--   exist and this script becomes a no-op.

-- Create the database only if it does not exist
SELECT 'CREATE DATABASE ai-sentinel WITH\n'
       || '  OWNER ' || quote_ident(current_user) || '\n'
       || '  TEMPLATE template0\n'
       || '  ENCODING ''UTF8''\n'
       || '  LC_COLLATE ''C''\n'
       || '  LC_CTYPE ''C'';' 
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'ai-sentinel')
\gexec

-- Optional: connect to the newly created (or existing) database and apply base settings
\connect ai-sentinel

-- Keep timestamps consistent
ALTER DATABASE "ai-sentinel" SET timezone = 'UTC';

-- Extensions commonly used (safe if extension already exists)
CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- for future hashing/UUID utilities
