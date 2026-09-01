package pro.softcom.aisentinel.application.pii.reporting.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.reporting.port.in.PauseScanPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PersonallyIdentifiableInformationScanExecutionOrchestratorPort;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;

/**
 * Use case for pausing a running scan.
 * Business purpose: Allow users to pause an ongoing scan, saving the current progress
 * so it can be resumed later.
 */
@RequiredArgsConstructor
@Slf4j
public class PauseScanUseCase implements PauseScanPort {

    private final ScanCheckpointRepository scanCheckpointRepository;
    private final PersonallyIdentifiableInformationScanExecutionOrchestratorPort personallyIdentifiableInformationScanExecutionOrchestratorPort;

    @Override
    public void pauseScan(String scanId) {
        if (isBlank(scanId)) {
            log.warn("[PAUSE] Cannot pause scan: scanId is blank");
            return;
        }

        log.info("[PAUSE] Pausing scan {}", scanId);

        // Step 1: Dispose the reactive subscription to stop emitting new events
        boolean disposed = personallyIdentifiableInformationScanExecutionOrchestratorPort.pauseScan(scanId);
        if (!disposed) {
            log.warn("[PAUSE] Scan {} not found or already completed", scanId);
        }

        // Step 2: Atomic UPDATE — sets every unfinished checkpoint to PAUSED in a single SQL statement.
        // This eliminates the TOCTOU race condition where in-flight scan events could overwrite
        // a PAUSED status between a read and a write.
        int updated = scanCheckpointRepository.pauseUnfinishedCheckpoints(scanId);

        if (updated == 0) {
            log.info("[PAUSE] No unfinished checkpoint found for scan {} - scan may already be completed or paused", scanId);
        } else {
            log.info("[PAUSE] Scan {} paused: {} checkpoint(s) moved to PAUSED", scanId, updated);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
