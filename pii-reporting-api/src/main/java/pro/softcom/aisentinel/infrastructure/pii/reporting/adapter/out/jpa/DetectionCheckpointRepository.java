package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa;

import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanCheckpointEntity;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanCheckpointId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DetectionCheckpointRepository extends
    JpaRepository<@NonNull ScanCheckpointEntity, @NonNull ScanCheckpointId> {

    Optional<ScanCheckpointEntity> findByScanIdAndSpaceKey(String scanId, String spaceKey);

    List<ScanCheckpointEntity> findByScanIdOrderBySpaceKey(String scanId);

    List<ScanCheckpointEntity> findBySpaceKeyOrderByUpdatedAtDesc(String spaceKey);

    Optional<ScanCheckpointEntity> findFirstBySpaceKeyOrderByUpdatedAtDesc(String spaceKey);

    /**
     * Finds the latest checkpoint for every space key using a window function logic.
     * Business purpose: Aggregate the global state of all spaces from their most recent scans.
     */
    @Query(value = """
        SELECT DISTINCT ON (space_key) *
        FROM scan_checkpoints
        ORDER BY space_key, updated_at DESC
        """, nativeQuery = true)
    List<ScanCheckpointEntity> findAllLatestCheckpoints();

    void deleteByScanId(String scanId);

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
     * Deletes ALL scan checkpoints for specific spaces regardless of status.
     * Business purpose: When re-scanning selected spaces, all previous checkpoint data
     * (including COMPLETED) must be removed so the dashboard summary does not return
     * stale statuses for those spaces before the new scan creates its own checkpoints.
     *
     * @param spaceKeys list of space keys to purge
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ScanCheckpointEntity s WHERE s.spaceKey IN :spaceKeys")
    void deleteAllCheckpointsForSpaces(@Param("spaceKeys") List<String> spaceKeys);

    /**
     * Marks all RUNNING or PAUSED checkpoints NOT in the given space list as INTERRUPTED.
     * Business purpose: When starting a selected scan, stale active checkpoints from
     * previous interrupted scans on other spaces must be resolved to prevent them from
     * polluting the dashboard summary with ghost RUNNING statuses. Interrupted work is
     * marked INTERRUPTED, never COMPLETED, so partially scanned spaces are never presented
     * as fully scanned (which would hide the PII on their unscanned pages).
     * All scan data (results, counts, progress) is preserved — only the status changes.
     *
     * @param spaceKeys list of space keys that are EXCLUDED from the cleanup (being re-scanned)
     * @return number of rows updated
     */
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE scan_checkpoints
        SET status = 'INTERRUPTED', updated_at = NOW()
        WHERE status IN ('RUNNING', 'PAUSED') AND space_key NOT IN (:spaceKeys)
        """, nativeQuery = true)
    int resolveStaleActiveCheckpoints(@Param("spaceKeys") List<String> spaceKeys);

    /**
     * Atomically sets every unfinished checkpoint of a scan to PAUSED.
     * Uses a single UPDATE statement — no TOCTOU race condition possible.
     *
     * <p>NOT_STARTED is included, not only RUNNING: an outage that strikes while
     * the first space is being listed leaves the whole scope NOT_STARTED, and
     * restricting the update to RUNNING would then write nothing at all. The
     * dashboard reads its state from these rows, so the scan would keep reporting
     * itself as in progress with no way to resume it.
     *
     * @param scanId the scan identifier
     * @return number of rows updated
     */
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE scan_checkpoints
        SET status = 'PAUSED', updated_at = NOW()
        WHERE scan_id = :scanId AND status IN ('RUNNING', 'NOT_STARTED')
        """, nativeQuery = true)
    int pauseUnfinishedCheckpoints(@Param("scanId") String scanId);

    /**
     * Atomically sets ALL PAUSED checkpoints for a scan to RUNNING.
     * Called by ResumeScanUseCase BEFORE restarting the scan, so the UPSERT guard
     * does not reject subsequent scan events.
     */
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE scan_checkpoints
        SET status = 'RUNNING', updated_at = NOW()
        WHERE scan_id = :scanId AND status = 'PAUSED'
        """, nativeQuery = true)
    int resumeAllPausedCheckpoints(@Param("scanId") String scanId);

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
     *   <li><strong>status:</strong> Updated to reflect current scan state, EXCEPT when current status
     *       is PAUSED and incoming status is RUNNING (prevents race condition where in-flight scan events
     *       overwrite a user-initiated pause)</li>
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
            status = CASE WHEN scan_checkpoints.status = 'PAUSED' AND :status = 'RUNNING' THEN 'PAUSED' ELSE :status END,
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