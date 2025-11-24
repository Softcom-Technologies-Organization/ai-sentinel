package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Composite identifier for ScanEventEntity (scanId, event_seq).
 */
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ScanEventId implements Serializable {
    private String scanId;
    private long eventSeq;
}
