package pro.softcom.aisentinel.domain.pii.reporting;

import java.time.Instant;
import java.util.List;

/**
 * Scan reporting summary combining scan metadata with per-space aggregated data.
 * This is the single source of truth for dashboard display, combining:
 * - Authoritative status and progress from scan_checkpoints
 * - Aggregated counters from scan_events
 */
public record ScanReportingSummary(
    String scanId,
    Instant lastUpdated,
    int spacesCount,
    List<SpaceSummary> spaces
) {
}
