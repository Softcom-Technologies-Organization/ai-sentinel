package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA Entity for PII detection configuration.
 * Represents the database table for storing PII detection settings.
 * Single-row configuration table (id always = 1).
 */
@Setter
@Getter
@Entity
@Table(name = "pii_detection_config")
@Builder
@AllArgsConstructor
public class PiiDetectionConfigEntity {

    @Id
    @Column(nullable = false)
    private Integer id;

    @NotNull
    @Column(name = "gliner_enabled", nullable = false)
    private Boolean glinerEnabled;

    @NotNull
    @Column(name = "presidio_enabled", nullable = false)
    private Boolean presidioEnabled;

    @NotNull
    @Column(name = "regex_enabled", nullable = false)
    private Boolean regexEnabled;

    @NotNull
    @DecimalMin(value = "0.0", message = "Default threshold must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Default threshold must be at most 1.0")
    @Column(name = "default_threshold", nullable = false, precision = 3, scale = 2)
    private BigDecimal defaultThreshold;

    @Column(name = "nb_of_label_by_pass", nullable = false)
    @NotNull
    @Min(value = 1, message = "nbOfLabelByPass must be >= 1")
    private Integer nbOfLabelByPass;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    protected PiiDetectionConfigEntity() {
        // Required by JPA
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PiiDetectionConfigEntity that = (PiiDetectionConfigEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}