package pro.softcom.aisentinel.domain.pii.judge;

/**
 * Lifecycle status of a judge job. At most one {@code RUNNING} job may exist per space; a
 * cancelled job and a job found {@code RUNNING} at boot are both {@code INTERRUPTED}, and the
 * verdicts already recorded stay.
 */
public enum JudgeJobStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    INTERRUPTED
}
