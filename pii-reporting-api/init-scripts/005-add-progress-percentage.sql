-- Migration: Add progress_percentage column to scan_checkpoints table
-- Purpose: Persist scan progress percentage for accurate display after scan resumption
-- Date: 2025-11-20

ALTER TABLE scan_checkpoints 
ADD COLUMN IF NOT EXISTS progress_percentage DOUBLE PRECISION;

COMMENT ON COLUMN scan_checkpoints.progress_percentage IS 'Percentage of scan completion (0.0 to 100.0). Persisted from ScanResult.analysisProgressPercentage during scan execution.';
