package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO for updating PII detection configuration via REST API.
 * 
 * <p>Business purpose: Allows clients to modify detector enable/disable states
 * and confidence thresholds used by the PII detection service.
 * 
 * <p>Validation: Ensures at least one detector is enabled and threshold is within valid range.
 * 
 * @param glinerEnabled    Whether GLiNER detector should be enabled
 * @param presidioEnabled  Whether Presidio detector should be enabled
 * @param regexEnabled     Whether custom regex detector should be enabled
 * @param defaultThreshold Default confidence threshold (0.0 to 1.0)
 */
public record UpdatePiiDetectionConfigRequestDto(
    @JsonProperty("glinerEnabled")
    @NotNull(message = "glinerEnabled is required")
    Boolean glinerEnabled,
    
    @JsonProperty("presidioEnabled")
    @NotNull(message = "presidioEnabled is required")
    Boolean presidioEnabled,
    
    @JsonProperty("regexEnabled")
    @NotNull(message = "regexEnabled is required")
    Boolean regexEnabled,
    
    @JsonProperty("defaultThreshold")
    @NotNull(message = "defaultThreshold is required")
    @DecimalMin(value = "0.0", message = "Default threshold must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Default threshold must be at most 1.0")
    BigDecimal defaultThreshold
) {
    /**
     * Validates business rules for the configuration request.
     * 
     * <p>Business rule: At least one detector must be enabled to ensure
     * the system can perform PII detection.
     * 
     * @throws IllegalArgumentException if no detectors are enabled
     */
    public UpdatePiiDetectionConfigRequestDto {
        if (glinerEnabled != null && presidioEnabled != null && regexEnabled != null) {
            if (!glinerEnabled && !presidioEnabled && !regexEnabled) {
                throw new IllegalArgumentException("At least one detector must be enabled");
            }
        }
    }
}
