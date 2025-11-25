package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.pii.reporting.ScanSeverityCountService;
import pro.softcom.aisentinel.domain.pii.reporting.ScanReportingSummary;
import pro.softcom.aisentinel.domain.pii.reporting.SeverityCounts;
import pro.softcom.aisentinel.domain.pii.reporting.SpaceSummary;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ScanReportingSummaryDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.SeverityCountsDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.SpaceSummaryDto;

/**
 * Maps domain DashboardSummary to presentation DashboardSummaryDto.
 * This keeps the domain independent from the web layer while preserving API contract.
 */
@Component
@RequiredArgsConstructor
public class ScanReportingSummaryMapper {

    private final ScanSeverityCountService severityCountService;
    private final SeverityCountsMapper severityCountsMapper;

    public ScanReportingSummaryDto toDto(ScanReportingSummary summary) {
        if (summary == null) {
            return null;
        }
        
        String scanId = summary.scanId();
        List<SpaceSummaryDto> spaceDtos = summary.spaces() != null
                ? summary.spaces().stream()
                    .map(space -> toSpaceDto(scanId, space))
                    .toList()
                : List.of();
        
        return new ScanReportingSummaryDto(
                summary.scanId(),
                summary.lastUpdated(),
                summary.spacesCount(),
                spaceDtos
        );
    }

    private SpaceSummaryDto toSpaceDto(String scanId, SpaceSummary space) {
        if (space == null) {
            return null;
        }
        
        Optional<SeverityCounts> countsOpt = severityCountService.getCounts(scanId, space.spaceKey());
        SeverityCounts counts = countsOpt.orElse(null);
        SeverityCountsDto severityCountsDto = severityCountsMapper.toDto(counts);
        
        return new SpaceSummaryDto(
                space.spaceKey(),
                space.status(),
                space.progressPercentage(),
                space.pagesDone(),
                space.attachmentsDone(),
                space.lastEventTs(),
                severityCountsDto
        );
    }
}
