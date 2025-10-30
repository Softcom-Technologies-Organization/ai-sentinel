package pro.softcom.sentinelle.application.pii.reporting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import pro.softcom.sentinelle.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.sentinelle.domain.pii.ScanStatus;
import pro.softcom.sentinelle.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;

/**
 * Manages scan checkpoint persistence for resume capability.
 * Business intent: Tracks scan progress to enable resuming interrupted scans.
 */
@RequiredArgsConstructor
@Slf4j
public class ScanCheckpointService {

    private final ScanCheckpointRepository scanCheckpointRepository;

    /**
     * Persists checkpoint based on scan event.
     *
     * @param scanResult the scan event to persist
     */
    public void persistCheckpoint(ScanResult scanResult) {
        if (!isValidForCheckpoint(scanResult)) {
            return;
        }

        try {
            ScanCheckpoint checkpoint = buildCheckpoint(scanResult);
            if (checkpoint != null) {
                scanCheckpointRepository.save(checkpoint);
                log.debug("[CHECKPOINT] Saved checkpoint for scan {}", scanResult.scanId());
            }
        } catch (Exception exception) {
            log.warn("[CHECKPOINT] Unable to persist checkpoint: {}", exception.getMessage());
        }
    }

    private boolean isValidForCheckpoint(ScanResult scanResult) {
        if (scanResult == null) {
            return false;
        }
        String scanId = scanResult.scanId();
        String spaceKey = scanResult.spaceKey();
        return !StringUtils.isBlank(scanId) && !StringUtils.isBlank(spaceKey);
    }

    private ScanCheckpoint buildCheckpoint(ScanResult scanResult) {
        String eventType = scanResult.eventType();
        if (eventType == null) {
            return null;
        }

        CheckpointData data = extractCheckpointData(eventType, scanResult);
        if (data == null) {
            return null;
        }

        return ScanCheckpoint.builder()
            .scanId(scanResult.scanId())
            .spaceKey(scanResult.spaceKey())
            .lastProcessedPageId(data.lastPage())
            .lastProcessedAttachmentName(data.lastAttachment())
            .scanStatus(data.status())
            .build();
    }

    private CheckpointData extractCheckpointData(String eventType, ScanResult scanResult) {
        return switch (eventType) {
            case "item" ->
                // Do NOT advance lastProcessedPageId on interim page item
                // Keep status as RUNNING and preserve existing lastProcessedPageId
                new CheckpointData(null, null, ScanStatus.RUNNING);
            case "attachmentItem" -> 
                // Persist attachment progress but do NOT advance lastProcessedPageId
                new CheckpointData(null, scanResult.attachmentName(), ScanStatus.RUNNING);
            case "pageComplete" -> 
                // Persist progress at end of page
                new CheckpointData(scanResult.pageId(), null, ScanStatus.RUNNING);
            case "complete" -> 
                // Space-level completion
                new CheckpointData(null, null, ScanStatus.COMPLETED);
            default -> null; // Ignore other events
        };
    }

    /**
     * Internal record for checkpoint data extraction.
     */
    private record CheckpointData(String lastPage, String lastAttachment, ScanStatus status) {
    }
}
