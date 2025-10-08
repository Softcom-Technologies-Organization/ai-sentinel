package pro.softcom.sentinelle.application.pii.reporting.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.application.pii.reporting.port.in.PauseScanUseCase;
import pro.softcom.sentinelle.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.sentinelle.domain.pii.ScanStatus;
import pro.softcom.sentinelle.domain.pii.reporting.ScanCheckpoint;

/**
 * Implementation of pause scan use case.
 * Business intent: Updates all space checkpoints to PAUSED status when user interrupts a scan.
 */
@RequiredArgsConstructor
@Slf4j
public class PauseScanUseCaseImpl implements PauseScanUseCase {

    private final ScanCheckpointRepository scanCheckpointRepository;

    @Override
    public void pauseScan(String scanId) {
        if (isBlank(scanId)) {
            log.warn("[PAUSE] Cannot pause scan: scanId is blank");
            return;
        }

        log.info("[PAUSE] Pausing scan {}", scanId);
        
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
