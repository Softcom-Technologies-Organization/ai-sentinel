package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa;

import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanSeverityCountEntity;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanSeverityCountId;

/**
 * JPA repository for atomic persistence of PII severity counts.
 * 
 * <p>Provides thread-safe increment operations using PostgreSQL's UPSERT mechanism
 * (INSERT ... ON CONFLICT DO UPDATE) to ensure reliable count aggregation during
 * concurrent scan processing.
 */
@Repository
public interface ScanSeverityCountJpaRepository extends
    JpaRepository<@NonNull ScanSeverityCountEntity, @NonNull ScanSeverityCountId> {

    /**
     * Atomically increments severity counts for a scan-space combination using PostgreSQL's UPSERT.
     * 
     * <p><strong>Business Purpose:</strong>
     * Enables concurrent scan workers to safely increment severity counts without race conditions.
     * Multiple workers may process different pages/attachments in the same space simultaneously,
     * and this operation ensures all increments are correctly applied without data loss.
     * 
     * <p><strong>Insert Behavior (New Record):</strong>
     * When no record exists for the scan-space combination, creates a new entry with the
     * provided delta values as initial counts. All count fields are initialized to 0 if
     * their respective delta is 0.
     * 
     * <p><strong>Update Behavior (Existing Record - ON CONFLICT):</strong>
     * When a record already exists (composite key conflict on scan_id + space_key),
     * atomically adds the delta values to the existing counts:
     * <ul>
     *   <li><strong>nb_of_high_severity:</strong> Incremented by deltaHigh</li>
     *   <li><strong>nb_of_medium_severity:</strong> Incremented by deltaMedium</li>
     *   <li><strong>nb_of_low_severity:</strong> Incremented by deltaLow</li>
     * </ul>
     * 
     * <p><strong>Concurrency Safety:</strong>
     * The ON CONFLICT DO UPDATE clause provides database-level atomicity. Multiple concurrent
     * transactions attempting to increment the same scan-space record will be serialized by
     * PostgreSQL's row-level locking, preventing lost updates.
     * 
     * <p><strong>Performance Characteristics:</strong>
     * - Single database round-trip per increment operation
     * - Row-level locking (not table-level) for high concurrency
     * - O(1) operation complexity regardless of existing count values
     * 
     * <p><strong>Example Usage:</strong>
     * During scan processing, when a worker detects 3 HIGH and 1 MEDIUM PII in a page:
     * <pre>
     * incrementCounts("scan-123", "SPACE-KEY", 3, 1, 0);
     * </pre>
     * This will atomically add these counts to any existing counts for the same scan-space.
     * 
     * @param scanId      Unique scan identifier (must not be null or blank)
     * @param spaceKey    Confluence space key (must not be null or blank)
     * @param deltaHigh   Number of HIGH severity PIIs to add (may be 0, must not be negative)
     * @param deltaMedium Number of MEDIUM severity PIIs to add (may be 0, must not be negative)
     * @param deltaLow    Number of LOW severity PIIs to add (may be 0, must not be negative)
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        INSERT INTO scan_severity_counts (scan_id, space_key, nb_of_high_severity, nb_of_medium_severity, nb_of_low_severity)
        VALUES (:scanId, :spaceKey, :deltaHigh, :deltaMedium, :deltaLow)
        ON CONFLICT (scan_id, space_key) DO UPDATE
        SET nb_of_high_severity = scan_severity_counts.nb_of_high_severity + :deltaHigh,
            nb_of_medium_severity = scan_severity_counts.nb_of_medium_severity + :deltaMedium,
            nb_of_low_severity = scan_severity_counts.nb_of_low_severity + :deltaLow
        """, nativeQuery = true)
    void incrementCounts(@Param("scanId") String scanId,
                         @Param("spaceKey") String spaceKey,
                         @Param("deltaHigh") int deltaHigh,
                         @Param("deltaMedium") int deltaMedium,
                         @Param("deltaLow") int deltaLow);

    /**
     * Retrieves all severity count records for a given scan.
     * 
     * <p>Used for dashboard display showing per-space statistics for a scan.
     * Results are ordered by space key for consistent display.
     * 
     * @param scanId Unique scan identifier
     * @return List of severity count entities (may be empty)
     */
    List<ScanSeverityCountEntity> findById_ScanIdOrderById_SpaceKey(String scanId);

    /**
     * Deletes all severity count records for a given scan.
     * 
     * <p>Business purpose: Cleanup operation when a scan is deleted or restarted.
     * Cascades deletion to all spaces associated with the scan.
     * 
     * @param scanId Unique scan identifier
     */
    void deleteById_ScanId(String scanId);
}
