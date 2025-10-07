package pro.softcom.sentinelle.application.pii.reporting.service;

import lombok.RequiredArgsConstructor;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanEventStore;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.scan.ContentPiiDetection;

/**
 * Orchestrates scan event lifecycle: creation, progress tracking, and persistence.
 * Business purpose: Coordinates the generation of scan events and manages scan checkpoints
 * to enable resumable scans and progress reporting.
 */
@RequiredArgsConstructor
public class ScanOrchestrator {

    private final ScanEventFactory scanEventFactory;
    private final ScanProgressCalculator scanProgressCalculator;
    private final ScanCheckpointService scanCheckpointService;
    private final ScanEventStore scanEventStore;

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

    public ScanResult createErrorEvent(String scanId, String spaceKey, String pageId,
                                      String message, double progress) {
        return scanEventFactory.createErrorEvent(scanId, spaceKey, pageId, message, progress);
    }

    public double calculateProgress(int analyzed, int total) {
        return scanProgressCalculator.calculateProgress(analyzed, total);
    }

    public void persistEventAndCheckpoint(ScanResult event) {
        scanCheckpointService.persistCheckpoint(event);
        if (scanEventStore != null) {
            scanEventStore.append(event);
        }
    }
}
