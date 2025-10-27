package pro.softcom.sentinelle.application.pii.scan.port.out;

import java.util.List;
import java.util.Optional;
import pro.softcom.sentinelle.domain.pii.reporting.ScanCheckpoint;

/**
 * Port sortant: persistance des points de reprise (checkpoint) d'un scan.
 */
public interface ScanCheckpointRepository {

    void save(ScanCheckpoint checkpoint);

    Optional<ScanCheckpoint> findByScanAndSpace(String scanId, String spaceKey);

    List<ScanCheckpoint> findByScan(String scanId);

    List<ScanCheckpoint> findBySpace(String spaceKey);

    void deleteByScan(String scanId);
}
