package pro.softcom.aisentinel.application.pii.reporting.port.out;

import pro.softcom.aisentinel.domain.pii.reporting.ScanDetectorStat;
import pro.softcom.aisentinel.domain.pii.reporting.ScanDetectorStatDelta;
import pro.softcom.aisentinel.domain.pii.reporting.ScanSpaceStats;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Application out port to accumulate and read per-space scan statistics.
 *
 * <p>Business purpose: maintains volume, failure and per-detector throughput
 * counters per (scan, space) so the dashboard can display a scan tooltip.
 *
 * <p>All increment operations MUST be atomic at the database level: attachments
 * of a page are processed in parallel, so concurrent increments on the same
 * (scan, space) row must never lose updates. Implementations rely on SQL upsert
 * (INSERT ... ON CONFLICT DO UPDATE SET x = x + :delta) rather than
 * read-modify-write in memory.
 */
public interface ScanSpaceStatsRepository {

    /**
     * Records the start of a space scan, setting {@code started_at} on first
     * sight and leaving it untouched on resume.
     *
     * @param scanId    unique scan identifier
     * @param spaceKey  Confluence space key
     * @param startedAt moment the space scan started
     */
    void markStarted(String scanId, String spaceKey, Instant startedAt);

    /**
     * Records the completion of a space scan, setting {@code finished_at}.
     *
     * @param scanId     unique scan identifier
     * @param spaceKey   Confluence space key
     * @param finishedAt moment the space scan completed
     */
    void markFinished(String scanId, String spaceKey, Instant finishedAt);

    /**
     * Atomically increments page counters for a successfully scanned page.
     *
     * @param scanId    unique scan identifier
     * @param spaceKey  Confluence space key
     * @param charCount number of characters of page content analyzed
     */
    void incrementPageScanned(String scanId, String spaceKey, long charCount);

    /**
     * Atomically increments the failed-page counter.
     *
     * @param scanId   unique scan identifier
     * @param spaceKey Confluence space key
     */
    void incrementPageFailed(String scanId, String spaceKey);

    /**
     * Atomically increments attachment counters for a successfully scanned attachment.
     *
     * @param scanId    unique scan identifier
     * @param spaceKey  Confluence space key
     * @param charCount number of characters of attachment content analyzed
     */
    void incrementAttachmentScanned(String scanId, String spaceKey, long charCount);

    /**
     * Atomically increments the failed-attachment counter.
     *
     * @param scanId   unique scan identifier
     * @param spaceKey Confluence space key
     */
    void incrementAttachmentFailed(String scanId, String spaceKey);

    /**
     * Atomically accumulates per-detector stats from a single analysis request.
     *
     * @param scanId   unique scan identifier
     * @param spaceKey Confluence space key
     * @param delta    this request's contribution for one detector
     */
    void accumulateDetectorStat(String scanId, String spaceKey, ScanDetectorStatDelta delta);

    /**
     * Reads the aggregated stats row for a (scan, space) pair.
     *
     * @param scanId   unique scan identifier
     * @param spaceKey Confluence space key
     * @return the stats without detector breakdown and failed items, or empty
     *         when no stats row exists for the pair
     */
    Optional<ScanSpaceStats> findStats(String scanId, String spaceKey);

    /**
     * Reads the per-detector stats for a (scan, space) pair.
     *
     * @param scanId   unique scan identifier
     * @param spaceKey Confluence space key
     * @return per-detector stats (may be empty)
     */
    List<ScanDetectorStat> findDetectorStats(String scanId, String spaceKey);
}
