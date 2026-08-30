package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto;

/**
 * Per-detector throughput stats exposed in the dashboard space tooltip.
 *
 * @param detector       detector identifier (e.g. MINISTRAL); also the POSTFILTER post-filter
 * @param detections     total raw entities found (or examined count for POSTFILTER)
 * @param charsProcessed total characters submitted to this detector
 * @param busyMs         cumulated busy time of this detector in milliseconds
 * @param charsPerSecond throughput in characters per second, or null when no busy time
 * @param discarded      total PII discarded by this stage (0 for real detectors)
 * @param failedRequests analysis requests this detector could not serve; non-zero means
 *                       the scan is incomplete for this detector
 * @param lastError      latest failure reason, null when the detector never failed
 */
public record ScanDetectorStatDto(
    String detector,
    int detections,
    long charsProcessed,
    long busyMs,
    Double charsPerSecond,
    int discarded,
    int failedRequests,
    String lastError
) { }
