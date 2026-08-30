package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pro.softcom.aisentinel.domain.pii.reporting.ScanDetectorStatDelta;

import java.time.Instant;

/**
 * JPA entity for per-detector cumulated scan statistics.
 *
 * <p>Maps to {@code scan_detector_stats}. {@code busyMs} is the summed
 * per-detector busy time across the scan's analysis requests.
 */
@Entity
@Table(name = "scan_detector_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanDetectorStatsEntity {

    @EmbeddedId
    private ScanDetectorStatsId id;

    @Column(name = "busy_ms", nullable = false)
    private Long busyMs;

    @Column(name = "chars_processed", nullable = false)
    private Long charsProcessed;

    @Column(name = "detections", nullable = false)
    private Integer detections;

    @Column(name = "discarded", nullable = false)
    private Integer discarded;

    /**
     * Analysis requests this detector could not serve (e.g. endpoint unreachable).
     *
     * <p>The SQL default is declared here, not only in the init script: those scripts
     * run from {@code docker-entrypoint-initdb.d} and therefore only on a fresh data
     * directory, so on an existing database it is {@code ddl-auto: update} that adds
     * this column — and PostgreSQL rejects adding a NOT NULL column without a default
     * to a table that already has rows.
     */
    @Column(name = "failed_requests", nullable = false, columnDefinition = "integer default 0")
    private Integer failedRequests;

    /**
     * Latest failure reason, kept as the diagnosable detail behind the counter.
     *
     * <p>Bounded to the JPA default length, which is what {@code ddl-auto: update}
     * creates; {@link pro.softcom.aisentinel.domain.pii.reporting.ScanDetectorStatDelta}
     * truncates to the same bound so an unusually verbose reason cannot make the
     * stats insert fail (which would silently lose the failure record).
     */
    @Column(name = "last_error", length = ScanDetectorStatDelta.MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
