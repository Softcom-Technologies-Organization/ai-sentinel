package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA Entity for PII detection configuration.
 * Represents the database table for storing PII detection settings.
 * Single-row configuration table (id always = 1).
 */
@Entity
@Table(name = "pii_detection_config")
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

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    protected PiiDetectionConfigEntity() {
        // Required by JPA
    }

    public PiiDetectionConfigEntity(Integer id, Boolean glinerEnabled, Boolean presidioEnabled,
                                    Boolean regexEnabled, BigDecimal defaultThreshold,
                                    LocalDateTime updatedAt, String updatedBy) {
        this.id = id;
        this.glinerEnabled = glinerEnabled;
        this.presidioEnabled = presidioEnabled;
        this.regexEnabled = regexEnabled;
        this.defaultThreshold = defaultThreshold;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Boolean getGlinerEnabled() {
        return glinerEnabled;
    }

    public void setGlinerEnabled(Boolean glinerEnabled) {
        this.glinerEnabled = glinerEnabled;
    }

    public Boolean getPresidioEnabled() {
        return presidioEnabled;
    }

    public void setPresidioEnabled(Boolean presidioEnabled) {
        this.presidioEnabled = presidioEnabled;
    }

    public Boolean getRegexEnabled() {
        return regexEnabled;
    }

    public void setRegexEnabled(Boolean regexEnabled) {
        this.regexEnabled = regexEnabled;
    }

    public BigDecimal getDefaultThreshold() {
        return defaultThreshold;
    }

    public void setDefaultThreshold(BigDecimal defaultThreshold) {
        this.defaultThreshold = defaultThreshold;
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
        PiiDetectionConfigEntity that = (PiiDetectionConfigEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
