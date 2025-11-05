package pro.softcom.sentinelle.application.pii.scan.port.out;

import java.util.List;
import java.util.Optional;
import pro.softcom.sentinelle.domain.pii.reporting.ScanCheckpoint;

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
     * Deletes all checkpoints for the given scan.
     *
     * @param scanId the business identifier of the scan
     */
    void deleteByScan(String scanId);
}
