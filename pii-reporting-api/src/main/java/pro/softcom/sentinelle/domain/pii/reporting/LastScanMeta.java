package pro.softcom.sentinelle.domain.pii.reporting;

import java.time.Instant;

/**
 * Lightweight descriptor for the latest scan.
 */
public record LastScanMeta(String scanId, Instant lastUpdated, int spacesCount) {

}
