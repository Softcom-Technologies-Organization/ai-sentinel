package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite primary key for {@link ScanSeverityCountEntity}.
 * 
 * <p>Combines scan identifier and space key to uniquely identify severity counts
 * for a specific scan-space combination.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanSeverityCountId implements Serializable {

    @Column(name = "scan_id", nullable = false, length = 255)
    private String scanId;

    @Column(name = "space_key", nullable = false, length = 255)
    private String spaceKey;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScanSeverityCountId that = (ScanSeverityCountId) o;
        return Objects.equals(scanId, that.scanId) && Objects.equals(spaceKey, that.spaceKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scanId, spaceKey);
    }
}
