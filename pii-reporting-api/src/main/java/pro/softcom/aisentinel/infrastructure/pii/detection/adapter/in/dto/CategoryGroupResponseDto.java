package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto;

import java.util.List;

/**
 * Response DTO for a category group containing PII types.
 * Part of the nested structure for UI display.
 */
public record CategoryGroupResponseDto(
        String category,
        List<PiiTypeConfigResponseDto> types
) {
}
