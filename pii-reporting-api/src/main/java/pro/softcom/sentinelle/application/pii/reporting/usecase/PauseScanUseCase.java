package pro.softcom.sentinelle.application.pii.reporting.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.application.pii.reporting.port.in.PauseScanPort;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanTaskManager;
import pro.softcom.sentinelle.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.sentinelle.domain.pii.ScanStatus;
import pro.softcom.sentinelle.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.sentinelle.domain.pii.scan.IllegalScanStatusTransitionException;
import pro.softcom.sentinelle.domain.pii.scan.Initiator;
import pro.softcom.sentinelle.domain.pii.scan.ScanCheckpointStatusTransition;

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
        
        // Dispose the reactive subscription to stop the scan in background task
        boolean disposed = scanTaskManager.pauseScan(scanId);
        if (!disposed) {
            log.warn("[PAUSE] Scan {} not found or already completed", scanId);
        }
        
        // Update checkpoints in database to persist PAUSED status
        var checkpoints = scanCheckpointRepository.findByScan(scanId);
        int pausedCount = 0;
        int skippedCount = 0;
        
        for (ScanCheckpoint checkpoint : checkpoints) {
            try {
                ScanCheckpointStatusTransition transition =
                    new ScanCheckpointStatusTransition(checkpoint.scanStatus(), Initiator.USER);
                
                ScanStatus newStatus = transition.transition(ScanStatus.PAUSED);
                
                ScanCheckpoint pausedCheckpoint = ScanCheckpoint.builder()
                    .scanId(checkpoint.scanId())
                    .spaceKey(checkpoint.spaceKey())
                    .lastProcessedPageId(checkpoint.lastProcessedPageId())
                    .lastProcessedAttachmentName(checkpoint.lastProcessedAttachmentName())
                    .scanStatus(newStatus)
                    .progressPercentage(checkpoint.progressPercentage())
                    .build();
                
                scanCheckpointRepository.save(pausedCheckpoint);
                pausedCount++;
                log.debug("[PAUSE] Updated checkpoint for space {} to PAUSED", checkpoint.spaceKey());
                
            } catch (IllegalScanStatusTransitionException e) {
                skippedCount++;
                log.debug("[PAUSE] Skipped checkpoint for space {} ({})", 
                    checkpoint.spaceKey(), e.getMessage());
            }
        }
        
        log.info("[PAUSE] Scan {} paused: {} spaces updated, {} spaces skipped (already completed/failed)", 
            scanId, pausedCount, skippedCount);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
