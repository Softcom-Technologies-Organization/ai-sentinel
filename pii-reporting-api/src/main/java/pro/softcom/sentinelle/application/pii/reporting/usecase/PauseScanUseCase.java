package pro.softcom.sentinelle.application.pii.reporting.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.application.pii.reporting.port.in.PauseScanPort;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanTaskManager;
import pro.softcom.sentinelle.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.sentinelle.domain.pii.ScanStatus;
import pro.softcom.sentinelle.domain.pii.reporting.ScanCheckpoint;

@RequiredArgsConstructor
@Slf4j
public class PauseScanUseCase implements PauseScanPort {

    private final ScanCheckpointRepository scanCheckpointRepository;
    private final ScanTaskManager scanTaskManager;

    @Override
    public void pauseScan(String scanId) {
        if (isBlank(scanId)) {
            log.warn("[PAUSE] Cannot pause scan: scanId is blank");
            return;
        }

        log.info("[PAUSE] Pausing scan {}", scanId);
        
        // Dispose la souscription réactive pour arrêter le scan en tâche de fond
        boolean disposed = scanTaskManager.pauseScan(scanId);
        if (!disposed) {
            log.warn("[PAUSE] Scan {} not found or already completed", scanId);
        }
        
        // Met à jour les checkpoints en base de données pour persister l'état PAUSED
        var checkpoints = scanCheckpointRepository.findByScan(scanId);
        
        for (ScanCheckpoint checkpoint : checkpoints) {
            if (checkpoint.scanStatus() != ScanStatus.COMPLETED 
                && checkpoint.scanStatus() != ScanStatus.FAILED) {
                
                ScanCheckpoint pausedCheckpoint = ScanCheckpoint.builder()
                    .scanId(checkpoint.scanId())
                    .spaceKey(checkpoint.spaceKey())
                    .lastProcessedPageId(checkpoint.lastProcessedPageId())
                    .lastProcessedAttachmentName(checkpoint.lastProcessedAttachmentName())
                    .scanStatus(ScanStatus.PAUSED)
                    .progressPercentage(checkpoint.progressPercentage())
                    .build();
                
                scanCheckpointRepository.save(pausedCheckpoint);
                log.debug("[PAUSE] Updated checkpoint for space {} to PAUSED", checkpoint.spaceKey());
            }
        }
        
        log.info("[PAUSE] Scan {} paused successfully ({} spaces)", scanId, checkpoints.size());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
