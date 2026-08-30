package pro.softcom.aisentinel.domain.pii.reporting;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Aggregated execution stats for a single detector within a space scan.
 *
 * <p>Business purpose: lets the dashboard compare detectors by throughput
 * (characters processed per second of busy time) and detection volume.
 *
 * @param detector       detector identifier (e.g. PRESIDIO, REGEX, MINISTRAL); also the
 *                       POSTFILTER post-filter, surfaced as a pseudo detector
 * @param detections     total raw entities found by this detector (or examined count
 *                       for the POSTFILTER post-filter)
 * @param charsProcessed total characters submitted to this detector
 * @param busyMs         cumulated busy time of this detector in milliseconds
 * @param discarded      total PII discarded by this stage (0 for real detectors)
 * @param failedRequests number of analysis requests this detector could not serve
 *                       (e.g. its endpoint was unreachable); a non-zero value means
 *                       the scan is incomplete for this detector
 * @param lastError      latest failure reason, null when the detector never failed
 */
public record ScanDetectorStat(
    String detector,
    int detections,
    long charsProcessed,
    long busyMs,
    int discarded,
    int failedRequests,
    String lastError
) {

    /**
     * Throughput of this detector in characters per second of busy time.
     *
     * @return characters per second rounded to one decimal, or null when no
     *         busy time was recorded (division by zero is undefined)
     */
    public Double charsPerSecond() {
        if (busyMs <= 0) {
            return null;
        }
        return BigDecimal.valueOf(charsProcessed * 1000.0 / busyMs)
            .setScale(1, RoundingMode.HALF_UP)
            .doubleValue();
    }
}
