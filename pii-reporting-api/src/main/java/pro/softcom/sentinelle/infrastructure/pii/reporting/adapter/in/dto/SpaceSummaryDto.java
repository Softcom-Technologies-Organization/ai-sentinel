package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto;

import java.time.Instant;

public record SpaceSummaryDto(
        String spaceKey,
        String status,
        Double progressPercentage,
        long pagesDone,
        long attachmentsDone,
        Instant lastEventTs
) { }
