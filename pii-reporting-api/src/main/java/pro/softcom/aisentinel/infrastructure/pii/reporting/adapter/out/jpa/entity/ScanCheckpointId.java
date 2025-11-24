package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity;

import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Composite identifier for ScanCheckpointEntity (scanId, spaceKey).
 * Infrastructure-level ID used by JPA; not exposed to the domain.
 */
@Getter
@Setter
@EqualsAndHashCode
@RequiredArgsConstructor
public class ScanCheckpointId implements Serializable {
    private String scanId;
    private String spaceKey;
}
