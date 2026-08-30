package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper;

import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.domain.pii.reporting.FailedScanItem;
import pro.softcom.aisentinel.domain.pii.reporting.ScanDetectorStat;
import pro.softcom.aisentinel.domain.pii.reporting.ScanSpaceStats;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.FailedScanItemDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ScanDetectorStatDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ScanSpaceStatsDto;

import java.util.List;

/**
 * Maps the domain {@link ScanSpaceStats} read model to its REST DTO.
 *
 * <p>Computes presentation-only derived values (duration, throughput) from the
 * domain model so the web contract stays independent from the core.
 */
@Component
public class ScanSpaceStatsMapper {

    public ScanSpaceStatsDto toDto(ScanSpaceStats stats) {
        if (stats == null) {
            return null;
        }
        return new ScanSpaceStatsDto(
            stats.scanId(),
            stats.spaceKey(),
            stats.startedAt(),
            stats.finishedAt(),
            stats.durationMs(),
            stats.pagesScanned(),
            stats.pagesFailed(),
            stats.pageChars(),
            stats.attachmentsScanned(),
            stats.attachmentsFailed(),
            stats.attachmentChars(),
            toFailedItemDtos(stats.failedItems()),
            toDetectorStatDtos(stats.detectorStats()));
    }

    private List<FailedScanItemDto> toFailedItemDtos(List<FailedScanItem> failedItems) {
        if (failedItems == null) {
            return List.of();
        }
        return failedItems.stream()
            .map(item -> new FailedScanItemDto(item.itemType().name(), item.title()))
            .toList();
    }

    private List<ScanDetectorStatDto> toDetectorStatDtos(List<ScanDetectorStat> detectorStats) {
        if (detectorStats == null) {
            return List.of();
        }
        return detectorStats.stream()
            .map(stat -> new ScanDetectorStatDto(
                stat.detector(),
                stat.detections(),
                stat.charsProcessed(),
                stat.busyMs(),
                stat.charsPerSecond(),
                stat.discarded(),
                stat.failedRequests(),
                stat.lastError()))
            .toList();
    }
}
