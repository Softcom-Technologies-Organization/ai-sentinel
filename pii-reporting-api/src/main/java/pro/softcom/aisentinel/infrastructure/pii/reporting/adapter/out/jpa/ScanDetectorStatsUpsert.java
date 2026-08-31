package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa;

import pro.softcom.aisentinel.domain.pii.reporting.ScanDetectorStatDelta;

/**
 * Atomic accumulation of one analysis request's detector stats.
 *
 * <p>Declared as a repository fragment rather than a derived query because the
 * accumulation is a PostgreSQL UPSERT over ten columns: expressed as an annotated
 * query it would take the delta apart into one bind parameter per counter.
 */
public interface ScanDetectorStatsUpsert {

    /**
     * Accumulates one analysis request's stats for a detector.
     *
     * <p>{@code lastError} is only overwritten when non-empty, so a later successful
     * request never erases the reason a previous one failed: the failure must stay
     * visible for the whole scan, not just until the detector recovers.
     *
     * @param scanId   scan the stats belong to
     * @param spaceKey space the stats belong to
     * @param delta    the request's contribution to the detector's totals
     */
    void accumulate(String scanId, String spaceKey, ScanDetectorStatDelta delta);
}
