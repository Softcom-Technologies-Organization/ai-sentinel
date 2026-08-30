package pro.softcom.aisentinel.application.pii.reporting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.reporting.ScanPiiTypeCountService;
import pro.softcom.aisentinel.application.pii.reporting.ScanSeverityCountService;
import pro.softcom.aisentinel.application.pii.reporting.SeverityCalculationService;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanEventStore;
import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.SeverityCounts;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.ScanEventType;
import pro.softcom.aisentinel.domain.pii.scan.TranslatableError;

/**
 * Orchestrates scan event lifecycle: creation, progress tracking, and persistence.
 * Business purpose: Coordinates the generation of scan events and manages scan checkpoints
 * to enable resumable scans and progress reporting.
 * Additionally calculates and persists severity counts for PII detections.
 */
@RequiredArgsConstructor
@Slf4j
public class ContentScanOrchestrator {

    private final ScanEventFactory scanEventFactory;
    private final ScanProgressCalculator scanProgressCalculator;
    private final ScanCheckpointService scanCheckpointService;
    private final ScanEventStore scanEventStore;
    private final ScanEventDispatcher scanEventDispatcher;
    private final SeverityCalculationService severityCalculationService;
    private final ScanSeverityCountService scanSeverityCountService;
    private final ScanPiiTypeCountService scanPiiTypeCountService;

    public ConfluenceContentScanResult createStartEvent(String scanId, String spaceKey, int total, double progress) {
        return scanEventFactory.createStartEvent(scanId, spaceKey, total, progress);
    }

    public ConfluenceContentScanResult createCompleteEvent(String scanId, String spaceKey) {
        return scanEventFactory.createCompleteEvent(scanId, spaceKey);
    }

    public ConfluenceContentScanResult createPageStartEvent(String scanId, String spaceKey, ConfluencePage page,
                                                            int currentIndex, int total, double progress) {
        return scanEventFactory.createPageStartEvent(scanId, spaceKey, page, currentIndex, total, progress);
    }

    public ConfluenceContentScanResult createPageCompleteEvent(String scanId, String spaceKey, ConfluencePage page,
                                                               double progress) {
        return scanEventFactory.createPageCompleteEvent(scanId, spaceKey, page, progress);
    }

    public ConfluenceContentScanResult createPageItemEvent(String scanId, String spaceKey, ConfluencePage page,
                                                           String content, ContentPiiDetection detection, double progress) {
        return scanEventFactory.createPageItemEvent(scanId, spaceKey, page, content, detection, progress);
    }

    public ConfluenceContentScanResult createEmptyPageItemEvent(String scanId, String spaceKey, ConfluencePage page,
                                                                double progress) {
        return scanEventFactory.createEmptyPageItemEvent(scanId, spaceKey, page, progress);
    }

    public ConfluenceContentScanResult createAttachmentItemEvent(String scanId, String spaceKey, ConfluencePage page,
                                                                 AttachmentInfo attachment,
                                                                 String content, ContentPiiDetection detection,
                                                                 double progress) {
        return scanEventFactory.createAttachmentItemEvent(scanId, spaceKey, page, attachment, content,
                detection, progress);
    }

    public ConfluenceContentScanResult createErrorEvent(String scanId, String spaceKey, String pageId,
                                                        TranslatableError error, double progress) {
        return scanEventFactory.createErrorEvent(scanId, spaceKey, pageId, error, progress);
    }

    public double calculateProgress(int analyzed, int total) {
        return scanProgressCalculator.calculateProgress(analyzed, total);
    }

    /**
     * Pauses a scan stopped by an outage, so the Resume button can pick it up.
     *
     * <p>Failures are swallowed on purpose: the scan is already stopping, and an
     * exception here would replace a diagnosable "scan paused" message with an
     * unrelated persistence error.
     *
     * @param scanId the scan to pause
     */
    public void pauseScanAfterOutage(String scanId) {
        try {
            int paused = scanCheckpointService.pauseRunningCheckpoints(scanId);
            log.warn("[SCAN] Scan {} paused after an outage: {} checkpoint(s) moved to PAUSED", scanId, paused);
        } catch (Exception exception) {
            log.error("[SCAN] Failed to pause scan {} after an outage: {}", scanId, exception.getMessage());
        }
    }

    /**
     * Persists checkpoint synchronously to ensure consistent state for scan resume.
     * 
     * <p>Business purpose: The checkpoint MUST be persisted synchronously to avoid race conditions
     * when the user refreshes the page during an active scan. If checkpoint persistence were async,
     * a refresh could read stale checkpoint data and re-scan pages that were already processed,
     * leading to duplicated severity counts.
     * 
     * @param event the scan event containing checkpoint data
     */
    public void persistCheckpointSynchronously(ConfluenceContentScanResult event) {
        try {
            scanCheckpointService.persistCheckpoint(event);
        } catch (Exception e) {
            log.warn("[CHECKPOINT] Failed to persist checkpoint synchronously: {}", e.getMessage());
        }
    }

    /**
     * Persists severity counts, event store, and dispatches notifications.
     * 
     * <p>Business purpose: These operations can be executed asynchronously as they are
     * additive (severity counts increment) or non-critical for scan resume (event store).
     * This allows the SSE stream to remain responsive while background persistence occurs.
     * 
     * @param event the scan event to process
     */
    public void persistEventAsyncOperations(ConfluenceContentScanResult event) {
        // Calculate and persist severity counts if event contains PII detections
        if (event.detectedPIIs() != null && !event.detectedPIIs().isEmpty()) {
            SeverityCounts counts = severityCalculationService.aggregateCounts(event.detectedPIIs());
            scanSeverityCountService.incrementCounts(event.scanId(), event.spaceKey(), counts);
        }

        // Persist per-type occurrence counts if event carries them
        if (event.detectedPiiCountByType() != null && !event.detectedPiiCountByType().isEmpty()) {
            scanPiiTypeCountService.incrementCounts(event.scanId(), event.spaceKey(), event.detectedPiiCountByType());
        }

        if (scanEventStore != null) {
            scanEventStore.append(event);

            // Has findings?
            if (shouldPublishEvent(event)) {
                // Publish the event only if transaction successfully committed
                scanEventDispatcher.publishAfterCommit(event.scanId(), event.spaceKey());
            }
        }
    }

    private static boolean shouldPublishEvent(ConfluenceContentScanResult event) {
        return ScanEventType.COMPLETE.getValue().equals(event.eventType());
    }

    /**
     * Purges previous scan data to ensure a clean state before starting a new scan.
     */
    public void purgePreviousScanData() {
        try {
            log.info("[SCAN] Purging previous active scan data before starting new scan");
            scanCheckpointService.deleteActiveScanCheckpoints();
            log.info("[SCAN] Previous scan data purged successfully");
        } catch (Exception e) {
            log.error("[SCAN] Failed to purge previous scan data: {}", e.getMessage(), e);
            // Don't fail the scan if purge fails - log and continue
        }
    }

    /**
     * Purges previous scan data for selected spaces to ensure a clean state before starting a new scan.
     * 
     * @param spaceKeys list of space keys to purge
     */
    /**
     * Records the scan scope server-side by creating a NOT_STARTED checkpoint for every space of a
     * fresh scan, before any space is scanned. This lets a paused scan be resumed within its original
     * scope and prevents the resume path from leaking into unselected spaces.
     *
     * @param scanId    the fresh scan identifier
     * @param spaceKeys the space keys forming the scan scope
     */
    public void initializeScanScope(String scanId, java.util.List<String> spaceKeys) {
        try {
            scanCheckpointService.initializeScanScope(scanId, spaceKeys);
        } catch (Exception e) {
            log.error("[SCAN] Failed to initialize scan scope for {}: {}", scanId, e.getMessage(), e);
            // Don't fail the scan if scope initialization fails - log and continue
        }
    }

    public void purgePreviousScanDataForSpaces(java.util.List<String> spaceKeys) {
        try {
            log.info("[SCAN] Purging ALL previous scan data for selected spaces before starting new scan");
            scanCheckpointService.deleteAllCheckpointsForSpaces(spaceKeys);
            int resolved = scanCheckpointService.resolveStaleActiveCheckpoints(spaceKeys);
            if (resolved > 0) {
                log.info("[SCAN] Resolved {} stale RUNNING/PAUSED checkpoints from previous scans", resolved);
            }
            log.info("[SCAN] Previous scan data for selected spaces purged successfully");
        } catch (Exception e) {
            log.error("[SCAN] Failed to purge previous scan data for selected spaces: {}", e.getMessage(), e);
            // Don't fail the scan if purge fails - log and continue
        }
    }
}