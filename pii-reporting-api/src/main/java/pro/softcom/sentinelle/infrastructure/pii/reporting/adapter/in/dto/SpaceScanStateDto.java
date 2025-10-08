package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto;

import java.time.Instant;

/**
 * Web DTO representing the status of a Confluence space for a given scan.
 * Business-oriented fields: status label and simple counters for progress display.
 */
public record SpaceScanStateDto(
        String spaceKey,
        String status,
        long pagesDone,
        long attachmentsDone,
        Instant lastEventTs
) { }
