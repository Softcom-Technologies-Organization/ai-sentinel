package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto;

import java.time.Instant;

public record SpaceScanStateDto(
        String spaceKey,
        String status,
        long pagesDone,
        long attachmentsDone,
        Instant lastEventTs
) { }
