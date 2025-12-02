package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO representing PII detection configuration for REST API responses.
 * 
 * <p>Business purpose: Provides clients with current detector enable/disable states
 * and confidence thresholds used by the PII detection service.
 * 
 * @param glinerEnabled    Whether GLiNER detector is enabled
 * @param presidioEnabled  Whether Presidio detector is enabled
 * @param regexEnabled     Whether custom regex detector is enabled
 * @param defaultThreshold Default confidence threshold (0.0 to 1.0)
 * @param updatedAt        Timestamp of last configuration update
 * @param updatedBy        User who last updated the configuration
 */
public record PiiDetectionConfigResponseDto(
    boolean glinerEnabled,
    boolean presidioEnabled,
    boolean regexEnabled,
    BigDecimal defaultThreshold,
    LocalDateTime updatedAt,
    String updatedBy
) {
}
