-- ============================================================================
-- LLM judge: verdict columns on the remediation projection and the judge job
-- journal — additive, idempotent migration.
-- ============================================================================
-- The schema is primarily managed by Hibernate ddl-auto
-- (spring.jpa.hibernate.ddl-auto=update); this script keeps Docker-bootstrapped
-- environments consistent and is safe to run multiple times.
-- ============================================================================

-- 1. Judge conclusion on a finding. A PENDING row with a verdict is a finding the
--    judge kept; PROPOSED_FALSE_POSITIVE is a rejection awaiting a reviewer.
ALTER TABLE pii_finding_remediation
    ADD COLUMN IF NOT EXISTS judge_verdict VARCHAR(20),
    ADD COLUMN IF NOT EXISTS judge_confidence DOUBLE PRECISION;

COMMENT ON COLUMN pii_finding_remediation.judge_verdict IS
    'LLM judge verdict once the confidence threshold is honoured: TRUE_POSITIVE | FALSE_POSITIVE | UNSURE. NULL = never judged. The justification is not stored: it may quote the value.';
COMMENT ON COLUMN pii_finding_remediation.judge_confidence IS
    'LLM judge confidence in its verdict (0.0-1.0).';

-- 2. Judge job journal.
CREATE TABLE IF NOT EXISTS pii_judge_job (
    id VARCHAR(100) PRIMARY KEY,
    space_key VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    model VARCHAR(200),
    apply_mode VARCHAR(20) NOT NULL,
    confidence_threshold DOUBLE PRECISION NOT NULL,
    processed INTEGER NOT NULL DEFAULT 0,
    total INTEGER NOT NULL DEFAULT 0,
    false_positives INTEGER NOT NULL DEFAULT 0,
    errors INTEGER NOT NULL DEFAULT 0,
    actor VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pii_judge_job_space_status
    ON pii_judge_job(space_key, status);

COMMENT ON TABLE pii_judge_job IS
    'Journal of LLM judge runs over the pending findings of one space: model, apply mode, threshold and progression counters. Never contains PII values.';
COMMENT ON COLUMN pii_judge_job.status IS
    'Job status: RUNNING | COMPLETED | FAILED | INTERRUPTED. RUNNING jobs are marked INTERRUPTED at application boot.';

-- 3. Mutual exclusion: at most one RUNNING judge job per space (partial unique
--    index, not expressible through JPA annotations).
CREATE UNIQUE INDEX IF NOT EXISTS uq_pii_judge_job_running_per_space
    ON pii_judge_job(space_key) WHERE status = 'RUNNING';
