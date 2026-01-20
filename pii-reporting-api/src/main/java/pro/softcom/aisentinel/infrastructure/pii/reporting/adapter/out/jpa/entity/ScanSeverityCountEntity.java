package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

/**
 * JPA entity for persisting PII severity counts aggregated by scan and space.
 * 
 * <p>Maps to the {@code scan_severity_counts} table, storing pre-calculated
 * severity statistics for performance-optimized dashboard display.
 * 
 * <p>The composite primary key ensures one record per scan-space combination,
 * with atomic increment operations supporting concurrent scan workers.
 */
@Entity
@Table(name = "scan_severity_counts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanSeverityCountEntity {

    @EmbeddedId
    private ScanSeverityCountId id;

    @Column(name = "nb_of_high_severity", nullable = false)
    private Integer nbOfHighSeverity;

    @Column(name = "nb_of_medium_severity", nullable = false)
    private Integer nbOfMediumSeverity;

    @Column(name = "nb_of_low_severity", nullable = false)
    private Integer nbOfLowSeverity;

    /**
     * Convenience method to get scan ID from composite key.
     */
    public String getScanId() {
        return id != null ? id.getScanId() : null;
    }

    /**
     * Convenience method to get space key from composite key.
     */
    public String getSpaceKey() {
        return id != null ? id.getSpaceKey() : null;
    }
}
