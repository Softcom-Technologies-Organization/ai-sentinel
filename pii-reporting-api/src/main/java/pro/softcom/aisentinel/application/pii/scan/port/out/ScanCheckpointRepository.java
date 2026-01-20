package pro.softcom.aisentinel.application.pii.scan.port.out;

import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;

import java.util.List;
import java.util.Optional;

/**
 * Application out port to store and retrieve scan checkpoints.
 * Business purpose: keeps a per-scan and per-space position so a scan can
 * resume, reconcile progress, or clean up its state.
 */
public interface ScanCheckpointRepository {

    /**
     * Persists or updates a checkpoint.
     *
     * @param checkpoint the business snapshot to record (scan id, space, position)
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
     * Deletes active scan checkpoints (RUNNING or PAUSED status) for specific spaces.
     * Business purpose: Clean up active scans for specific spaces when starting a fresh selected scan.
     * 
     * @param spaceKeys list of space keys to purge
     */
    void deleteActiveScanCheckpointsForSpaces(List<String> spaceKeys);

    /**
     * Finds the checkpoint with RUNNING status for a given scan.
     * Business purpose: When pausing a scan, only the RUNNING checkpoint should be paused,
     * not COMPLETED or other status checkpoints.
     *
     * @param scanId the business identifier of the scan
     * @return the RUNNING checkpoint if found, otherwise empty
     */
    Optional<ScanCheckpoint> findRunningScanCheckpoint(String scanId);
}