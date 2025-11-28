package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanCheckpointEntity;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanCheckpointId;

@Repository
public interface DetectionCheckpointRepository extends
    JpaRepository<@NonNull ScanCheckpointEntity, @NonNull ScanCheckpointId> {

    Optional<ScanCheckpointEntity> findByScanIdAndSpaceKey(String scanId, String spaceKey);

    List<ScanCheckpointEntity> findByScanIdOrderBySpaceKey(String scanId);

    List<ScanCheckpointEntity> findBySpaceKeyOrderByUpdatedAtDesc(String spaceKey);

    Optional<ScanCheckpointEntity> findFirstBySpaceKeyOrderByUpdatedAtDesc(String spaceKey);

    void deleteByScanId(String scanId);

    /**
     * Finds the most recent scan that is in RUNNING or PAUSED status.
     * Business purpose: Detect if there's an active multi-space scan that should be resumed
     * instead of starting a new one, preventing duplicate scanId generation.
     * 
     * @return Optional containing the scanId of the most recent RUNNING or PAUSED scan
     */
    @Query("""
        SELECT s.scanId
        FROM ScanCheckpointEntity s
        WHERE s.status IN ('RUNNING', 'PAUSED')
        ORDER BY s.updatedAt DESC
        LIMIT 1
        """)
    Optional<String> findMostRecentActiveScanId();

    /**
     * Deletes all scan checkpoints with RUNNING or PAUSED status.
     * Business purpose: Clean up active scans when starting a fresh scan to prevent
     * data accumulation and ensure severity counts are accurate.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ScanCheckpointEntity s WHERE s.status IN ('RUNNING', 'PAUSED')")
    void deleteActiveScanCheckpoints();

    /**
     * Persists or updates a scan checkpoint using PostgreSQL's UPSERT mechanism.
     * 
     * <p>This method implements atomic checkpoint persistence for scan resume functionality.
     * It ensures that scan progress (last processed page and attachment) is reliably saved,
     * enabling the system to resume scans from the exact point of interruption.
     * 
     * <p><strong>Insert Behavior:</strong>
     * When no checkpoint exists for the given scan-space combination, creates a new record
     * with all provided values.
     * 
     * <p><strong>Update Behavior (ON CONFLICT):</strong>
     * When a checkpoint already exists (scan_id, space_key conflict), applies conditional updates:
     * <ul>
     *   <li><strong>last_processed_page_id:</strong> Updated only if new value is non-null and non-empty,
     *       otherwise preserves existing value to prevent regression in scan progress</li>
     *   <li><strong>last_processed_attachment_name:</strong> Updated only if new value is non-null and non-empty,
     *       otherwise preserves existing value to maintain attachment processing state</li>
     *   <li><strong>status:</strong> Always updated to reflect current scan state</li>
     *   <li><strong>progress_percentage:</strong> Updated only when a non-null value is provided,
     *       otherwise preserves the existing non-null value to avoid regression of progress</li>
     *   <li><strong>updated_at:</strong> Always updated to track last checkpoint modification</li>
     * </ul>
     * 
     * <p><strong>Concurrency Safety:</strong>
     * The ON CONFLICT clause provides database-level atomicity, preventing race conditions
     * when multiple scan processes attempt to update the same checkpoint simultaneously.
     * 
     * <p><strong>Business Rule:</strong> The conditional update logic ensures that scan progress
     * never regresses - if a page/attachment has already been processed, empty or null values
     * from subsequent checkpoints will not overwrite this progress information.
     * 
     * @param scanId unique identifier of the scan session
     * @param spaceKey Confluence space key being scanned
     * @param pageId last successfully processed Confluence page ID (may be null during initial scan or between pages)
     * @param attachmentName last successfully processed attachment name (may be null when no attachments are being processed)
     * @param status current scan status (e.g., "IN_PROGRESS", "COMPLETED", "FAILED")
     * @param updatedAt timestamp of checkpoint creation/update
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
        INSERT INTO scan_checkpoints (scan_id, space_key, last_processed_page_id, last_processed_attachment_name, status, progress_percentage, updated_at)
        VALUES (:scanId, :spaceKey, :pageId, :attachmentName, :status, :progressPercentage, :updatedAt)
        ON CONFLICT (scan_id, space_key) DO UPDATE
        SET last_processed_page_id = CASE WHEN :pageId IS NOT NULL AND :pageId != '' THEN :pageId ELSE scan_checkpoints.last_processed_page_id END,
            last_processed_attachment_name = CASE WHEN :attachmentName IS NOT NULL AND :attachmentName != '' THEN :attachmentName ELSE scan_checkpoints.last_processed_attachment_name END,
            status = :status,
            progress_percentage = CASE WHEN :progressPercentage IS NOT NULL THEN :progressPercentage ELSE scan_checkpoints.progress_percentage END,
            updated_at = :updatedAt
        """, nativeQuery = true)
    void upsertCheckpoint(@Param("scanId") String scanId,
                          @Param("spaceKey") String spaceKey,
                          @Param("pageId") String pageId,
                          @Param("attachmentName") String attachmentName,
                          @Param("status") String status,
                          @Param("progressPercentage") Double progressPercentage,
                          @Param("updatedAt") LocalDateTime updatedAt);
}
