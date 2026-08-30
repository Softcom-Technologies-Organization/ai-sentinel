-- ============================================================================
-- Per-detector failure tracking on scan_detector_stats — additive, idempotent.
-- ============================================================================
-- A detector whose backing endpoint dies mid-scan used to contribute zero
-- findings indistinguishably from a detector that legitimately found none, so an
-- incomplete scan read as a clean one. These columns record, per (scan, space,
-- detector), how many analysis requests the detector could not serve and why, so
-- the dashboard scan-metrics popover can flag the degraded run after the fact.
--
-- Concurrency: increments go through the same UPSERT as the other counters, so
-- row-level locking on the composite primary key serializes them.
-- ============================================================================

-- The DEFAULT is required, not cosmetic: this table is populated in existing
-- environments, and PostgreSQL rejects adding a NOT NULL column without one.
ALTER TABLE scan_detector_stats ADD COLUMN IF NOT EXISTS failed_requests INTEGER NOT NULL DEFAULT 0;

-- Latest failure reason observed for this detector; kept as the diagnosable
-- detail behind the counter (host/port, exception type). Width matches the JPA
-- mapping so both migration paths (this script on a fresh volume, ddl-auto on an
-- existing database) produce the same column.
ALTER TABLE scan_detector_stats ADD COLUMN IF NOT EXISTS last_error VARCHAR(255);
