package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto;

import java.util.List;

/**
 * Response DTO for grouped PII types by detector and category.
 * Used for UI display with nested accordions.
 */
public record GroupedPiiTypesResponseDto(
        String detector,
        List<CategoryGroupResponseDto> categories
) {
}
