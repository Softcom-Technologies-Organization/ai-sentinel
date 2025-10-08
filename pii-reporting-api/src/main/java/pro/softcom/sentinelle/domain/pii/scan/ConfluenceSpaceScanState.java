package pro.softcom.sentinelle.domain.pii.scan;

import java.time.Instant;

/**
 * Per-space status with simple progress counters.
 */
public record ConfluenceSpaceScanState(String spaceKey, String status, long pagesDone,
                                       long attachmentsDone, Instant lastEventTs) {
}
