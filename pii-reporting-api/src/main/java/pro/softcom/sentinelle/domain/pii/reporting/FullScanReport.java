package pro.softcom.sentinelle.domain.pii.reporting;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import pro.softcom.sentinelle.domain.pii.ScanStatus;

/**
 * Full report of a scan across Confluence spaces.
 * Business purpose: captures the overall outcome of a scan, including
 * time boundaries, global progress, per-space analysis results, and any errors.
 * This is a read model used by reporting and visualization.
 *
 * @param scanId unique business identifier of the scan
 * @param startTime timestamp when the scan started
 * @param endTime timestamp when the scan ended (may be null while running)
 * @param status overall status of the scan lifecycle
 * @param totalSpaces total number of spaces targeted by the scan
 * @param processedSpaces number of spaces processed so far
 * @param totalPages total number of pages targeted by the scan
 * @param processedPages number of pages processed so far
 * @param spaceResults per-space analysis results in business order
 * @param globalStatistics aggregated counters across all spaces (keyed by metric name)
 * @param errors list of human-readable error messages collected during the scan
 */
public record FullScanReport(
    String scanId,
    LocalDateTime startTime,
    LocalDateTime endTime,
    ScanStatus status,
    int totalSpaces,
    int processedSpaces,
    int totalPages,
    int processedPages,
    List<SpaceAnalysisResult> spaceResults,
    Map<String, Integer> globalStatistics,
    List<String> errors
) { }
