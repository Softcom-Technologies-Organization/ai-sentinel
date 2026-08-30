package pro.softcom.aisentinel.application.pii.reporting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanSpaceStatsRepository;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.ScanDetectorStatDelta;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorRunStat;
import pro.softcom.aisentinel.domain.pii.scan.ScanEventType;

import java.time.Instant;
import java.util.List;

/**
 * Accumulates per-space scan statistics from scan events.
 *
 * <p>Business purpose: derives volume, failure and per-detector throughput
 * counters from the scan event stream and persists them atomically, so the
 * dashboard can later display a per-space scan tooltip.
 *
 * <p>This collector is invoked from the scan flux on a background scheduler.
 * Persistence failures are swallowed (logged at warn) so that statistics
 * collection can never make the scan itself fail.
 */
@RequiredArgsConstructor
@Slf4j
public class ScanSpaceStatsCollector {

    private final ScanSpaceStatsRepository repository;

    /**
     * Records the statistics carried by a single scan event.
     *
     * @param event the scan event to account for (ignored when null or missing
     *              its scan/space identifiers)
     */
    public void recordEvent(ConfluenceContentScanResult event) {
        if (event == null || event.scanId() == null || event.spaceKey() == null) {
            return;
        }
        try {
            dispatch(event);
        } catch (Exception exception) {
            log.warn("[SPACE_STATS] Failed to record scan space stats for scan={} space={}: {}",
                event.scanId(), event.spaceKey(), exception.getMessage());
        }
    }

    private void dispatch(ConfluenceContentScanResult event) {
        ScanEventType type = ScanEventType.fromValue(event.eventType());
        if (type == null) {
            return;
        }
        switch (type) {
            case START -> repository.markStarted(event.scanId(), event.spaceKey(), Instant.now());
            case COMPLETE -> repository.markFinished(event.scanId(), event.spaceKey(), Instant.now());
            case ITEM -> recordPageScanned(event);
            case ATTACHMENT_ITEM -> recordAttachmentScanned(event);
            case ERROR -> recordFailure(event);
            default -> {
                // Other event types (pageStart, pageComplete, ...) carry no stats.
            }
        }
    }

    private void recordPageScanned(ConfluenceContentScanResult event) {
        repository.incrementPageScanned(event.scanId(), event.spaceKey(), contentLength(event));
        accumulateDetectorStats(event);
    }

    private void recordAttachmentScanned(ConfluenceContentScanResult event) {
        repository.incrementAttachmentScanned(event.scanId(), event.spaceKey(), contentLength(event));
        accumulateDetectorStats(event);
    }

    private void recordFailure(ConfluenceContentScanResult event) {
        if (event.attachmentName() != null) {
            repository.incrementAttachmentFailed(event.scanId(), event.spaceKey());
            return;
        }
        // Scan-wide errors (a detector outage pausing the scan, a pre-flight refusal) carry no
        // page: counting them as a failed page would inflate the failure count of a space whose
        // pages were never even opened.
        if (event.pageId() != null) {
            repository.incrementPageFailed(event.scanId(), event.spaceKey());
        }
    }

    private void accumulateDetectorStats(ConfluenceContentScanResult event) {
        List<DetectorRunStat> stats = event.detectorRunStats();
        if (stats == null || stats.isEmpty()) {
            return;
        }
        long chars = contentLength(event);
        for (DetectorRunStat stat : stats) {
            if (stat.failed()) {
                log.warn("[SPACE_STATS] Detector {} failed for scan={} space={}: {}",
                    stat.source(), event.scanId(), event.spaceKey(), stat.error());
            }
            repository.accumulateDetectorStat(
                event.scanId(),
                event.spaceKey(),
                ScanDetectorStatDelta.from(stat, chars));
        }
    }

    private long contentLength(ConfluenceContentScanResult event) {
        return event.sourceContent() != null ? event.sourceContent().length() : 0L;
    }
}
