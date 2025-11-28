package pro.softcom.aisentinel.application.pii.reporting.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.reporting.port.in.PauseScanPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanTaskManager;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;

/**
 * Use case for pausing a running scan.
 * Business purpose: Allow users to pause an ongoing scan, saving the current progress
 * so it can be resumed later.
 */
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
        
        // Find the RUNNING checkpoint and update it to PAUSED
        var runningCheckpoint = scanCheckpointRepository.findRunningScanCheckpoint(scanId);
        
        if (runningCheckpoint.isEmpty()) {
            log.info("[PAUSE] No RUNNING checkpoint found for scan {} - scan may already be completed or paused", scanId);
            return;
        }
        
        ScanCheckpoint checkpoint = runningCheckpoint.get();
        ScanCheckpoint pausedCheckpoint = ScanCheckpoint.builder()
            .scanId(checkpoint.scanId())
            .spaceKey(checkpoint.spaceKey())
            .lastProcessedPageId(checkpoint.lastProcessedPageId())
            .lastProcessedAttachmentName(checkpoint.lastProcessedAttachmentName())
            .scanStatus(ScanStatus.PAUSED)
            .progressPercentage(checkpoint.progressPercentage())
            .build();
        
        scanCheckpointRepository.save(pausedCheckpoint);
        log.info("[PAUSE] Scan {} paused: space {} updated from RUNNING to PAUSED", 
            scanId, checkpoint.spaceKey());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
