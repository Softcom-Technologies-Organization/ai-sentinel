package pro.softcom.aisentinel.application.pii.reporting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Manages scan checkpoint persistence for resume capability.
 * Business intent: Tracks scan progress to enable resuming interrupted scans.
 */
@RequiredArgsConstructor
@Slf4j
public class ScanCheckpointService {

    private final ScanCheckpointRepository scanCheckpointRepository;

    /**
     * Persists checkpoint based on scan event reactively.
     * Uses boundedElastic scheduler to avoid blocking the event loop.
     *
     * @param confluenceContentScanResult the scan event to persist
     * @return Mono that completes when checkpoint is persisted
     */
    public Mono<Void> persistCheckpointReactive(
        ConfluenceContentScanResult confluenceContentScanResult) {
        return Mono.fromRunnable(() -> persistCheckpoint(confluenceContentScanResult))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /**
     * Persists checkpoint based on scan event.
     * Protected against thread interruptions to ensure checkpoint persistence
     * even when SSE client disconnects.
     *
     * @param confluenceContentScanResult the scan event to persist
     */
    public void persistCheckpoint(ConfluenceContentScanResult confluenceContentScanResult) {
        if (!isValidForCheckpoint(confluenceContentScanResult)) {
            return;
        }

        boolean wasInterrupted = false;
        try {
            // Clear interruption flag to allow DB operation to proceed
            if (Thread.interrupted()) {
                wasInterrupted = true;
                log.debug("[CHECKPOINT] Thread interrupted, clearing flag to persist checkpoint");
            }
            
            ScanCheckpoint checkpoint = buildCheckpoint(confluenceContentScanResult);
            if (checkpoint != null) {
                scanCheckpointRepository.save(checkpoint);
                log.debug("[CHECKPOINT] Saved checkpoint for scan {}", confluenceContentScanResult.scanId());
            }
        } catch (Exception exception) {
            log.warn("[CHECKPOINT] Unable to persist checkpoint: {}", exception.getMessage());
        } finally {
            // Restore interruption flag if it was set
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
                log.debug("[CHECKPOINT] Restored thread interrupt flag after checkpoint persistence");
            }
        }
    }

    private boolean isValidForCheckpoint(ConfluenceContentScanResult confluenceContentScanResult) {
        if (confluenceContentScanResult == null) {
            return false;
        }
        String scanId = confluenceContentScanResult.scanId();
        String spaceKey = confluenceContentScanResult.spaceKey();
        return !StringUtils.isBlank(scanId) && !StringUtils.isBlank(spaceKey);
    }

    private ScanCheckpoint buildCheckpoint(ConfluenceContentScanResult confluenceContentScanResult) {
        String eventType = confluenceContentScanResult.eventType();
        if (eventType == null) {
            return null;
        }

        CheckpointData data = extractCheckpointData(eventType, confluenceContentScanResult);
        if (data == null) {
            return null;
        }

        return ScanCheckpoint.builder()
            .scanId(confluenceContentScanResult.scanId())
            .spaceKey(confluenceContentScanResult.spaceKey())
            .lastProcessedPageId(data.lastPage())
            .lastProcessedAttachmentName(data.lastAttachment())
            .scanStatus(data.status())
            .progressPercentage(confluenceContentScanResult.analysisProgressPercentage())
            .build();
    }

    private CheckpointData extractCheckpointData(String eventType, ConfluenceContentScanResult confluenceContentScanResult) {
        return switch (eventType) {
            case "item" ->
                // Interim page item: persist checkpoint with RUNNING status
                // Pass null for lastProcessedPageId - repository merge strategy preserves existing value
                new CheckpointData(null, null, ScanStatus.RUNNING);
            case "attachmentItem" -> 
                // Persist attachment progress but do NOT advance lastProcessedPageId
                // Repository merge strategy preserves existing lastProcessedPageId
                new CheckpointData(null, confluenceContentScanResult.attachmentName(), ScanStatus.RUNNING);
            case "pageComplete" -> 
                // Persist progress at end of page - advance lastProcessedPageId
                new CheckpointData(confluenceContentScanResult.pageId(), null, ScanStatus.RUNNING);
            case "complete" -> 
                // Space-level completion - reset lastProcessedPageId
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
