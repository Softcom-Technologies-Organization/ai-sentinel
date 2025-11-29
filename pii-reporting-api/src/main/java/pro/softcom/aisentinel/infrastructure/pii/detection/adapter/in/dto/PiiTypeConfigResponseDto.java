package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto;

import pro.softcom.aisentinel.domain.pii.detection.PiiTypeConfig;

import java.time.LocalDateTime;

/**
 * Response DTO for PII type configuration.
 */
public record PiiTypeConfigResponseDto(
        Long id,
        String piiType,
        String detector,
        boolean enabled,
        double threshold,
        String displayName,
        String description,
        String category,
        String countryCode,
        LocalDateTime updatedAt,
        String updatedBy
) {
    public static PiiTypeConfigResponseDto fromDomain(PiiTypeConfig config) {
        return new PiiTypeConfigResponseDto(
                config.getId(),
                config.getPiiType(),
                config.getDetector(),
                config.isEnabled(),
                config.getThreshold(),
                config.getDisplayName(),
                config.getDescription(),
                config.getCategory(),
                config.getCountryCode(),
                config.getUpdatedAt(),
                config.getUpdatedBy()
        );
    }
}
