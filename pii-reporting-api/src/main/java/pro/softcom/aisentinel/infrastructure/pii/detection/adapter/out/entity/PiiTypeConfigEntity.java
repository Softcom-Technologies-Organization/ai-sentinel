package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pro.softcom.aisentinel.domain.pii.detection.PiiTypeConfig;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.PersonallyIdentifiableInformationType;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA entity for PII type configuration.
 * <p>
 * Maps to the pii_type_config database table.
 */
@Setter
@Getter
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
@NoArgsConstructor
public class PiiTypeConfigEntity {

    // Getters and setters
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pii_type", nullable = false, length = 100)
    @Enumerated(EnumType.STRING)
    private PersonallyIdentifiableInformationType piiType;

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

    @Column(name = "detector_label", length = 100)
    private String detectorLabel;

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

    // Factory method to create entity from domain model
    public static PiiTypeConfigEntity fromDomain(PiiTypeConfig domain) {
        PiiTypeConfigEntity entity = new PiiTypeConfigEntity();
        entity.id = domain.getId();
        entity.piiType = PersonallyIdentifiableInformationType.valueOf(domain.getPiiType());
        entity.detector = domain.getDetector();
        entity.enabled = domain.isEnabled();
        entity.threshold = domain.getThreshold();
        entity.displayName = domain.getDisplayName();
        entity.description = domain.getDescription();
        entity.category = domain.getCategory();
        entity.countryCode = domain.getCountryCode();
        entity.detectorLabel = domain.getDetectorLabel();
        entity.updatedAt = domain.getUpdatedAt();
        entity.updatedBy = domain.getUpdatedBy();
        return entity;
    }

    // Conversion method to domain model
    public PiiTypeConfig toDomain() {
        return PiiTypeConfig.builder()
                .id(id)
                .piiType(piiType.name())
                .detector(detector)
                .enabled(enabled)
                .threshold(threshold)
                .displayName(displayName)
                .description(description)
                .category(category)
                .countryCode(countryCode)
                .detectorLabel(detectorLabel)
                .updatedAt(updatedAt)
                .updatedBy(updatedBy)
                .build();
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