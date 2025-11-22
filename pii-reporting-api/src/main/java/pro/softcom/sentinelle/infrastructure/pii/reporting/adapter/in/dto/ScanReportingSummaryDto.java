package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto;

import java.time.Instant;
import java.util.List;

public record ScanReportingSummaryDto(
        String scanId,
        Instant lastUpdated,
        int spacesCount,
        List<SpaceSummaryDto> spaces
) { }
