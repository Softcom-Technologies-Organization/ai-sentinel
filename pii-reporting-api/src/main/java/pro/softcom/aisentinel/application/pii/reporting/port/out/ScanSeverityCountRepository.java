package pro.softcom.aisentinel.application.pii.reporting.port.out;

import java.util.List;
import java.util.Optional;
import pro.softcom.aisentinel.domain.pii.reporting.ScanSeverityCount;
import pro.softcom.aisentinel.domain.pii.reporting.SeverityCounts;

/**
 * Application out port to store and retrieve PII severity counts for scans.
 * 
 * <p>Business purpose: Maintains aggregated severity statistics per scan and space
 * for performance-optimized dashboard display and reporting, avoiding expensive
 * real-time calculations across large datasets.
 */
public interface ScanSeverityCountRepository {

    /**
     * Atomically increments severity counts for a given scan and space.
     * 
     * <p>This operation must be thread-safe and support concurrent increments
     * from multiple scan workers processing the same space. If no record exists,
     * creates a new one with the provided delta values.
     * 
     * @param scanId   Unique identifier of the scan
     * @param spaceKey Confluence space key
     * @param delta    Severity counts to add (must not be null)
     */
    void incrementCounts(String scanId, String spaceKey, SeverityCounts delta);

    /**
     * Retrieves severity counts for a specific scan and space.
     * 
     * @param scanId   Unique identifier of the scan
     * @param spaceKey Confluence space key
     * @return Severity counts if record exists, otherwise empty
     */
    Optional<SeverityCounts> findByScanIdAndSpaceKey(String scanId, String spaceKey);

    /**
     * Lists all severity counts for a given scan, grouped by space.
     * 
     * <p>Used for dashboard display showing per-space statistics for a scan.
     * 
     * @param scanId Unique identifier of the scan
     * @return List of scan severity counts (may be empty if scan has no data)
     */
    List<ScanSeverityCount> findByScanId(String scanId);

    /**
     * Deletes all severity count records for a given scan.
     * 
     * <p>Business purpose: Cleanup operation when a scan is deleted or restarted.
     * 
     * @param scanId Unique identifier of the scan to delete
     */
    void deleteByScanId(String scanId);
}
