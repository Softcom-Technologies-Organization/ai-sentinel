package pro.softcom.aisentinel.domain.pii.reporting;

import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorRunStat;

/**
 * One analysis request's contribution to a detector's cumulated scan stats.
 *
 * @param detector       detector identifier (e.g. MINISTRAL); also the POSTFILTER post-filter
 * @param busyMs         busy time of this detector for the request, in milliseconds
 * @param chars          characters submitted to this detector for the request
 * @param detections     raw entities found for the request (or examined count for POSTFILTER)
 * @param discarded      PII discarded by this stage for the request (0 for real detectors)
 * @param failedRequests 1 when the detector could not serve the request, 0 otherwise
 * @param lastError      failure reason when the detector failed, empty otherwise
 */
public record ScanDetectorStatDelta(
    String detector,
    long busyMs,
    long chars,
    int detections,
    int discarded,
    int failedRequests,
    String lastError
) {

    /**
     * Storage bound for {@code lastError}, matching the persisted column width.
     * A detector reason is a short technical sentence; truncating rather than
     * overflowing keeps the failure recorded instead of losing the whole stats row.
     */
    public static final int MAX_ERROR_LENGTH = 255;

    /**
     * Builds the delta a single detector run contributes for the analysed content.
     *
     * <p>A run that reported a failure counts as one failed request, so a detector
     * that was enabled but never usable is visible as such instead of looking like
     * a detector that simply found nothing.
     *
     * @param stat  the detector's run stats for one analysis request
     * @param chars characters submitted to the detector for that request
     * @return the corresponding accumulation delta
     */
    public static ScanDetectorStatDelta from(DetectorRunStat stat, long chars) {
        return new ScanDetectorStatDelta(
            stat.source().name(),
            stat.durationMs(),
            chars,
            stat.entitiesFound(),
            stat.entitiesDiscarded(),
            stat.failed() ? 1 : 0,
            stat.failed() ? truncateError(stat.error()) : "");
    }

    private static String truncateError(String error) {
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
