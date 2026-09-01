package pro.softcom.aisentinel.application.pii.scan.port.out;

import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;

import java.util.List;
import java.util.Optional;

/**
 * Application out port to store and retrieve scan checkpoints.
 * Business purpose: keeps a per-scan and per-space startingPosition so a scan can
 * resume, reconcile progress, or clean up its state.
 */
public interface ScanCheckpointRepository {

    /**
     * Persists or updates a checkpoint.
     *
     * @param checkpoint the business snapshot to record (scan id, space, startingPosition)
     */
    void save(ScanCheckpoint checkpoint);

    /**
     * Looks up the checkpoint for a given scan and space.
     *
     * @param scanId the business identifier of the scan
     * @param spaceKey the business key of the space
     * @return the checkpoint if present, otherwise empty
     */
    Optional<ScanCheckpoint> findByScanAndSpace(String scanId, String spaceKey);

    /**
     * Lists all checkpoints recorded for a scan.
     *
     * @param scanId the business identifier of the scan
     * @return checkpoints for the scan (may be empty)
     */
    List<ScanCheckpoint> findByScan(String scanId);

    /**
     * Lists all checkpoints recorded for a space across scans.
     *
     * @param spaceKey the business key of the space
     * @return checkpoints for the space (may be empty)
     */
    List<ScanCheckpoint> findBySpace(String spaceKey);

    /**
     * Finds the most recent checkpoint for a given space across all scans.
     * Business purpose: Determine the last scan date for a space to check if it needs re-scanning.
     *
     * @param spaceKey the business key of the space
     * @return the most recent checkpoint if present, otherwise empty
     */
    Optional<ScanCheckpoint> findLatestBySpace(String spaceKey);

    /**
     * Lists the most recent checkpoint for every space known in the system.
     * Business purpose: Build a global view of the latest state of all spaces, even if they belong to different scans.
     *
     * @return list of latest checkpoints (one per space)
     */
    List<ScanCheckpoint> findAllLatestCheckpoints();

    /**
     * Deletes all checkpoints for the given scan.
     *
     * @param scanId the business identifier of the scan
     */
    void deleteByScan(String scanId);

    /**
     * Deletes all active scan checkpoints (RUNNING or PAUSED status).
     * Business purpose: Clean up active scans when starting a fresh scan with the "Start" button.
     * This prevents accumulation of stale scan data and ensures severity counts don't get inflated
     * by mixing data from old and new scans.
     * Note: Completed scans (COMPLETED, FAILED status) are preserved as historical data.
     */
    void deleteActiveScanCheckpoints();

    /**
     * Deletes ALL scan checkpoints for specific spaces regardless of status.
     * Business purpose: When re-scanning selected spaces, all previous checkpoint data
     * (including COMPLETED) must be removed so the dashboard summary does not return
     * stale statuses before the new scan creates its own checkpoints.
     *
     * @param spaceKeys list of space keys to purge
     */
    void deleteAllCheckpointsForSpaces(List<String> spaceKeys);

    /**
     * Marks all RUNNING or PAUSED checkpoints NOT in the given space list as INTERRUPTED.
     * Business purpose: When starting a selected scan, stale active checkpoints from
     * previous interrupted scans on other spaces must be resolved to prevent them from
     * polluting the dashboard summary with ghost RUNNING statuses. Interrupted work is
     * marked INTERRUPTED, never COMPLETED, so partial scans are never presented as fully
     * scanned (which would hide the PII on their unscanned pages).
     * All scan data (results, counts, progress) is preserved — only the status changes.
     *
     * @param spaceKeys list of space keys EXCLUDED from cleanup (being re-scanned)
     * @return number of rows updated
     */
    int resolveStaleActiveCheckpoints(List<String> spaceKeys);

    /**
     * Atomically sets every unfinished checkpoint of a scan to PAUSED.
     * Business purpose: Race-condition-safe pause — uses a single UPDATE statement
     * so no in-flight scan event can overwrite the PAUSED status between read and write.
     *
     * <p>Unfinished means RUNNING or NOT_STARTED: a scan stopped before its first
     * space was opened has nothing RUNNING, and must still be reported as paused
     * rather than as forever in progress.
     *
     * @param scanId the business identifier of the scan
     * @return the number of checkpoints updated
     */
    int pauseUnfinishedCheckpoints(String scanId);

    /**
     * Atomically sets ALL PAUSED checkpoints for a scan to RUNNING.
     * Business purpose: On resume, update checkpoint status BEFORE the scan emits new events,
     * so the UPSERT guard (which blocks PAUSED → RUNNING from scan events) does not reject them.
     *
     * @param scanId the business identifier of the scan
     * @return the number of checkpoints updated
     */
    int resumeAllPausedCheckpoints(String scanId);
}