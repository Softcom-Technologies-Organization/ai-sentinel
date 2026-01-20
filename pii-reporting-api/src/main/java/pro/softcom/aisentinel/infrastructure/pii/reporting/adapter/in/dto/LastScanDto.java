package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto;

import java.time.Instant;

// DTOs used by web layer
public record LastScanDto(String scanId, Instant lastUpdated, int spacesCount) {

}
