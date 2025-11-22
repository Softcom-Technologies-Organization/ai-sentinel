package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.domain.pii.reporting.ScanReportingSummary;
import pro.softcom.sentinelle.domain.pii.reporting.SpaceSummary;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.ScanReportingSummaryDto;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.SpaceSummaryDto;

import java.util.List;

/**
 * Maps domain DashboardSummary to presentation DashboardSummaryDto.
 * This keeps the domain independent from the web layer while preserving API contract.
 */
@Component
@RequiredArgsConstructor
public class ScanReportingSummaryMapper {

    public ScanReportingSummaryDto toDto(ScanReportingSummary summary) {
        if (summary == null) {
            return null;
        }
        
        List<SpaceSummaryDto> spaceDtos = summary.spaces() != null
                ? summary.spaces().stream().map(this::toSpaceDto).toList()
                : List.of();
        
        return new ScanReportingSummaryDto(
                summary.scanId(),
                summary.lastUpdated(),
                summary.spacesCount(),
                spaceDtos
        );
    }

    private SpaceSummaryDto toSpaceDto(SpaceSummary space) {
        if (space == null) {
            return null;
        }
        
        return new SpaceSummaryDto(
                space.spaceKey(),
                space.status(),
                space.progressPercentage(),
                space.pagesDone(),
                space.attachmentsDone(),
                space.lastEventTs()
        );
    }
}
