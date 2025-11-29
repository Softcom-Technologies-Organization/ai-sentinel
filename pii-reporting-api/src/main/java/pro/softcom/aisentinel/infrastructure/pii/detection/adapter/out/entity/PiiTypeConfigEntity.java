package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.entity;

import jakarta.persistence.*;
import pro.softcom.aisentinel.domain.pii.detection.PiiTypeConfig;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA entity for PII type configuration.
 * <p>
 * Maps to the pii_type_config database table.
 */
@Entity
@Table(
        name = "pii_type_config",
        uniqueConstraints = @UniqueConstraint(
                name = "unique_type_detector",
                columnNames = {"pii_type", "detector"}
        ),
        indexes = {
                @Index(name = "idx_pii_type_config_detector", columnList = "detector"),
                @Index(name = "idx_pii_type_config_category", columnList = "category"),
                @Index(name = "idx_pii_type_config_country", columnList = "country_code")
        }
)
public class PiiTypeConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pii_type", nullable = false, length = 100)
    private String piiType;

    @Column(name = "detector", nullable = false, length = 50)
    private String detector;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "threshold", nullable = false)
    private double threshold;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "country_code", length = 10)
    private String countryCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", nullable = false, length = 255)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (updatedBy == null) {
            updatedBy = "system";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors
    public PiiTypeConfigEntity() {
    }

    // Factory method to create entity from domain model
    public static PiiTypeConfigEntity fromDomain(PiiTypeConfig domain) {
        PiiTypeConfigEntity entity = new PiiTypeConfigEntity();
        entity.id = domain.getId();
        entity.piiType = domain.getPiiType();
        entity.detector = domain.getDetector();
        entity.enabled = domain.isEnabled();
        entity.threshold = domain.getThreshold();
        entity.displayName = domain.getDisplayName();
        entity.description = domain.getDescription();
        entity.category = domain.getCategory();
        entity.countryCode = domain.getCountryCode();
        entity.updatedAt = domain.getUpdatedAt();
        entity.updatedBy = domain.getUpdatedBy();
        return entity;
    }

    // Conversion method to domain model
    public PiiTypeConfig toDomain() {
        return PiiTypeConfig.builder()
                .id(id)
                .piiType(piiType)
                .detector(detector)
                .enabled(enabled)
                .threshold(threshold)
                .displayName(displayName)
                .description(description)
                .category(category)
                .countryCode(countryCode)
                .updatedAt(updatedAt)
                .updatedBy(updatedBy)
                .build();
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPiiType() {
        return piiType;
    }

    public void setPiiType(String piiType) {
        this.piiType = piiType;
    }

    public String getDetector() {
        return detector;
    }

    public void setDetector(String detector) {
        this.detector = detector;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PiiTypeConfigEntity that = (PiiTypeConfigEntity) o;
        return Objects.equals(piiType, that.piiType) &&
                Objects.equals(detector, that.detector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(piiType, detector);
    }
}
