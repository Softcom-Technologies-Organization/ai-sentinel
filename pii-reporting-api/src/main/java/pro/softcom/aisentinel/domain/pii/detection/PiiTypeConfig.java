package pro.softcom.aisentinel.domain.pii.detection;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents configuration for a specific PII type within a detector.
 * <p>
 * Business rules:
 * - Each PII type + detector combination must be unique
 * - Threshold must be between 0.0 and 1.0
 * - Detector must be one of: GLINER, PRESIDIO, REGEX
 */
public class PiiTypeConfig {

    private final Long id;
    private final String piiType;
    private final String detector;
    private final boolean enabled;
    private final double threshold;
    private final String displayName;
    private final String description;
    private final String category;
    private final String countryCode;
    /**
     * Natural language label used by the detector for PII identification.
     * <p>
     * Business purpose: Decouples internal PII type codes from detector-specific labels.
     * For example, GLINER uses "email" while our system uses "EMAIL".
     * This enables runtime configuration of detector behavior without code changes.
     * <p>
     * Examples:
     * - "email" for EMAIL type
     * - "credit card number" for CREDITCARDNUMBER type
     * - "person name" for PERSONNAME type
     */
    private final String detectorLabel;
    private final LocalDateTime updatedAt;
    private final String updatedBy;

    private PiiTypeConfig(Builder builder) {
        this.id = builder.id;
        this.piiType = validatePiiType(builder.piiType);
        this.detector = validateDetector(builder.detector);
        this.enabled = builder.enabled;
        this.threshold = validateThreshold(builder.threshold);
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.category = builder.category;
        this.countryCode = builder.countryCode;
        this.detectorLabel = builder.detectorLabel;
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : LocalDateTime.now();
        this.updatedBy = builder.updatedBy != null ? builder.updatedBy : "system";
    }

    public static Builder builder() {
        return new Builder();
    }

    private String validatePiiType(String piiType) {
        if (piiType == null || piiType.trim().isEmpty()) {
            throw new IllegalArgumentException("PII type cannot be null or empty");
        }
        return piiType;
    }

    private String validateDetector(String detector) {
        if (detector == null) {
            throw new IllegalArgumentException("Detector cannot be null");
        }
        if (!detector.equals("GLINER") && !detector.equals("PRESIDIO") && !detector.equals("REGEX")) {
            throw new IllegalArgumentException(
                    "Detector must be one of: GLINER, PRESIDIO, REGEX. Got: " + detector);
        }
        return detector;
    }

    private double validateThreshold(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException(
                    "Threshold must be between 0.0 and 1.0. Got: " + threshold);
        }
        return threshold;
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getPiiType() {
        return piiType;
    }

    public String getDetector() {
        return detector;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getThreshold() {
        return threshold;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getDetectorLabel() {
        return detectorLabel;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    /**
     * Creates a copy with updated enable status and threshold.
     */
    public PiiTypeConfig withUpdate(boolean enabled, double threshold, String updatedBy) {
        return builder()
                .id(this.id)
                .piiType(this.piiType)
                .detector(this.detector)
                .enabled(enabled)
                .threshold(threshold)
                .displayName(this.displayName)
                .description(this.description)
                .category(this.category)
                .countryCode(this.countryCode)
                .detectorLabel(this.detectorLabel)
                .updatedAt(LocalDateTime.now())
                .updatedBy(updatedBy)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PiiTypeConfig that = (PiiTypeConfig) o;
        return Objects.equals(piiType, that.piiType) &&
                Objects.equals(detector, that.detector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(piiType, detector);
    }

    @Override
    public String toString() {
        return "PiiTypeConfig{" +
                "id=" + id +
                ", piiType='" + piiType + '\'' +
                ", detector='" + detector + '\'' +
                ", enabled=" + enabled +
                ", threshold=" + threshold +
                ", displayName='" + displayName + '\'' +
                ", category='" + category + '\'' +
                ", countryCode='" + countryCode + '\'' +
                ", updatedAt=" + updatedAt +
                ", updatedBy='" + updatedBy + '\'' +
                '}';
    }

    public static class Builder {
        private Long id;
        private String piiType;
        private String detector;
        private boolean enabled = true;
        private double threshold = 0.75;
        private String displayName;
        private String description;
        private String category;
        private String countryCode;
        private String detectorLabel;
        private LocalDateTime updatedAt;
        private String updatedBy;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder piiType(String piiType) {
            this.piiType = piiType;
            return this;
        }

        public Builder detector(String detector) {
            this.detector = detector;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder countryCode(String countryCode) {
            this.countryCode = countryCode;
            return this;
        }

        public Builder detectorLabel(String detectorLabel) {
            this.detectorLabel = detectorLabel;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder updatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        public PiiTypeConfig build() {
            return new PiiTypeConfig(this);
        }
    }
}
