package pro.softcom.aisentinel.application.pii.reporting.service;

import lombok.RequiredArgsConstructor;
import pro.softcom.aisentinel.application.pii.reporting.ScanSeverityCountService;
import pro.softcom.aisentinel.application.pii.reporting.SeverityCalculationService;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanEventStore;
import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.pii.reporting.ScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.SeverityCounts;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.ScanEventType;

/**
 * Orchestrates scan event lifecycle: creation, progress tracking, and persistence.
 * Business purpose: Coordinates the generation of scan events and manages scan checkpoints
 * to enable resumable scans and progress reporting.
 * Additionally calculates and persists severity counts for PII detections.
 */
@RequiredArgsConstructor
public class ContentScanOrchestrator {

    private final ScanEventFactory scanEventFactory;
    private final ScanProgressCalculator scanProgressCalculator;
    private final ScanCheckpointService scanCheckpointService;
    private final ScanEventStore scanEventStore;
    private final ScanEventDispatcher scanEventDispatcher;
    private final SeverityCalculationService severityCalculationService;
    private final ScanSeverityCountService scanSeverityCountService;

    public ScanResult createStartEvent(String scanId, String spaceKey, int total, double progress) {
        return scanEventFactory.createStartEvent(scanId, spaceKey, total, progress);
    }

    public ScanResult createCompleteEvent(String scanId, String spaceKey) {
        return scanEventFactory.createCompleteEvent(scanId, spaceKey);
    }

    public ScanResult createPageStartEvent(String scanId, String spaceKey, ConfluencePage page,
                                           int currentIndex, int total, double progress) {
        return scanEventFactory.createPageStartEvent(scanId, spaceKey, page, currentIndex, total, progress);
    }

    public ScanResult createPageCompleteEvent(String scanId, String spaceKey, ConfluencePage page,
                                              double progress) {
        return scanEventFactory.createPageCompleteEvent(scanId, spaceKey, page, progress);
    }

    public ScanResult createPageItemEvent(String scanId, String spaceKey, ConfluencePage page,
                                          String content, ContentPiiDetection detection, double progress) {
        return scanEventFactory.createPageItemEvent(scanId, spaceKey, page, content, detection, progress);
    }

    public ScanResult createEmptyPageItemEvent(String scanId, String spaceKey, ConfluencePage page,
                                               double progress) {
        return scanEventFactory.createEmptyPageItemEvent(scanId, spaceKey, page, progress);
    }

    public ScanResult createAttachmentItemEvent(String scanId, String spaceKey, ConfluencePage page,
                                                AttachmentInfo attachment,
                                                String content, ContentPiiDetection detection,
                                                double progress) {
        return scanEventFactory.createAttachmentItemEvent(scanId, spaceKey, page, attachment, content,
                detection, progress);
    }

    public ScanResult createErrorEvent(String scanId, String spaceKey, String pageId,
                                       String message, double progress) {
        return scanEventFactory.createErrorEvent(scanId, spaceKey, pageId, message, progress);
    }

    public double calculateProgress(int analyzed, int total) {
        return scanProgressCalculator.calculateProgress(analyzed, total);
    }

    public void persistEventAndCheckpoint(ScanResult event) {
        scanCheckpointService.persistCheckpoint(event);
        
        // Calculate and persist severity counts if event contains PII detections
        if (event.detectedEntities() != null && !event.detectedEntities().isEmpty()) {
            SeverityCounts counts = severityCalculationService.aggregateCounts(event.detectedEntities());
            scanSeverityCountService.incrementCounts(event.scanId(), event.spaceKey(), counts);
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

    private static boolean shouldPublishEvent(ScanResult event) {
        return ScanEventType.COMPLETE.getValue().equals(event.eventType());
    }
}
