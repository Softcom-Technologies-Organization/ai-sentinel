package pro.softcom.aisentinel.domain.pii.detection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain model for PII detection configuration.
 * Represents the configuration settings for PII detection detectors and thresholds.
 * This is the single source of truth for detection configuration in the system.
 */
public class PiiDetectionConfig {

    private static final BigDecimal MIN_THRESHOLD = BigDecimal.ZERO;
    private static final BigDecimal MAX_THRESHOLD = BigDecimal.ONE;

    private final Integer id;
    private final boolean glinerEnabled;
    private final boolean presidioEnabled;
    private final boolean regexEnabled;
    private final BigDecimal defaultThreshold;
    private final LocalDateTime updatedAt;
    private final String updatedBy;

    /**
     * Creates a new PII detection configuration.
     *
     * @param id               Configuration ID (always 1 for single-row config)
     * @param glinerEnabled    Whether GLiNER detector is enabled
     * @param presidioEnabled  Whether Presidio detector is enabled
     * @param regexEnabled     Whether custom regex detector is enabled
     * @param defaultThreshold Default confidence threshold (0.0 to 1.0)
     * @param updatedAt        Last update timestamp
     * @param updatedBy        User who last updated the configuration
     * @throws IllegalArgumentException if threshold is out of range
     */
    public PiiDetectionConfig(
            Integer id,
            boolean glinerEnabled,
            boolean presidioEnabled,
            boolean regexEnabled,
            BigDecimal defaultThreshold,
            LocalDateTime updatedAt,
            String updatedBy) {

        this.id = id;
        this.glinerEnabled = glinerEnabled;
        this.presidioEnabled = presidioEnabled;
        this.regexEnabled = regexEnabled;
        this.defaultThreshold = defaultThreshold;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;

        validate();
    }

    /**
     * Validates the configuration state.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public void validate() {
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
    }

    public Integer getId() {
        return id;
    }

    public boolean isGlinerEnabled() {
        return glinerEnabled;
    }

    public boolean isPresidioEnabled() {
        return presidioEnabled;
    }

    public boolean isRegexEnabled() {
        return regexEnabled;
    }

    public BigDecimal getDefaultThreshold() {
        return defaultThreshold;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PiiDetectionConfig that = (PiiDetectionConfig) o;
        return glinerEnabled == that.glinerEnabled &&
                presidioEnabled == that.presidioEnabled &&
                regexEnabled == that.regexEnabled &&
                Objects.equals(id, that.id) &&
                Objects.equals(defaultThreshold, that.defaultThreshold) &&
                Objects.equals(updatedAt, that.updatedAt) &&
                Objects.equals(updatedBy, that.updatedBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, glinerEnabled, presidioEnabled, regexEnabled,
                defaultThreshold, updatedAt, updatedBy);
    }

    @Override
    public String toString() {
        return "PiiDetectionConfig{" +
                "id=" + id +
                ", glinerEnabled=" + glinerEnabled +
                ", presidioEnabled=" + presidioEnabled +
                ", regexEnabled=" + regexEnabled +
                ", defaultThreshold=" + defaultThreshold +
                ", updatedAt=" + updatedAt +
                ", updatedBy='" + updatedBy + '\'' +
                '}';
    }
}
