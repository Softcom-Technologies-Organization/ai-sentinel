package pro.softcom.aisentinel.domain.pii.detection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Domain model for PII detection configuration.
 * Represents the configuration settings for PII detection detectors and thresholds.
 * This is the single source of truth for detection configuration in the system.
 */
public record PiiDetectionConfig(
        Integer id,
        boolean glinerEnabled,
        boolean presidioEnabled,
        boolean regexEnabled,
        BigDecimal defaultThreshold,
        Integer nbOfLabelByPass,
        LocalDateTime updatedAt,
        String updatedBy) {

    private static final BigDecimal MIN_THRESHOLD = BigDecimal.ZERO;
    private static final BigDecimal MAX_THRESHOLD = BigDecimal.ONE;

    /**
     * Compact constructor for validation.
     *
     * @throws IllegalArgumentException if threshold is out of range or other validation fails
     */
    public PiiDetectionConfig {
        if (defaultThreshold == null) {
            throw new IllegalArgumentException("Default threshold cannot be null");
        }

        if (defaultThreshold.compareTo(MIN_THRESHOLD) < 0) {
            throw new IllegalArgumentException(
                    "Default threshold must be greater than or equal to " + MIN_THRESHOLD);
        }

        if (defaultThreshold.compareTo(MAX_THRESHOLD) > 0) {
            throw new IllegalArgumentException(
                    "Default threshold must be less than or equal to " + MAX_THRESHOLD);
        }

        if (!glinerEnabled && !presidioEnabled && !regexEnabled) {
            throw new IllegalArgumentException(
                    "At least one detector must be enabled");
        }

        if (nbOfLabelByPass == null || nbOfLabelByPass < 1) {
            throw new IllegalArgumentException(
                    "Number of labels by pass must be at least 1");
        }
    }
}