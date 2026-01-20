package pro.softcom.aisentinel.domain.pii.reporting;

/**
 * Immutable record representing aggregated PII severity counts for a specific scan and space.
 * 
 * <p>Used for dashboard reporting and analytics, combining scan metadata with severity statistics.
 * 
 * @param scanId   Unique identifier of the scan
 * @param spaceKey Confluence space key
 * @param counts   Aggregated severity counts for this scan-space combination
 */
public record ScanSeverityCount(
    String scanId,
    String spaceKey,
    SeverityCounts counts
) {}
