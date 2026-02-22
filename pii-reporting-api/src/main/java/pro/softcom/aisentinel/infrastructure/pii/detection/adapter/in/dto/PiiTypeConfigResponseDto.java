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
        String category,
        String countryCode,
        String detectorLabel,
        boolean isCustom,
        String severity,
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
                config.getCategory(),
                config.getCountryCode(),
                config.getDetectorLabel(),
                config.isCustom(),
                config.getSeverity(),
                config.getUpdatedAt(),
                config.getUpdatedBy()
        );
    }
}