package pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity mapped to pii_finding_remediation table. Projection of the remediation lifecycle
 * of a finding; a missing row means the finding is implicitly PENDING.
 */
@Getter
@Setter
@Entity
@Table(name = "pii_finding_remediation", indexes = {
        @Index(name = "idx_pii_finding_remediation_space_status", columnList = "space_key,status"),
        @Index(name = "idx_pii_finding_remediation_page", columnList = "page_id")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindingRemediationEntity {

    @Id
    @Column(name = "finding_id", nullable = false, length = 64)
    private String findingId;

    @Column(name = "scan_id", nullable = false, length = 100)
    private String scanId;

    @Column(name = "space_key", nullable = false, length = 50)
    private String spaceKey;

    @Column(name = "page_id", nullable = false, length = 100)
    private String pageId;

    @Column(name = "attachment_name", length = 500)
    private String attachmentName;

    @Column(name = "pii_type", nullable = false, length = 100)
    private String piiType;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "detector", nullable = false, length = 50)
    private String detector;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "actor", nullable = false)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "redaction_job_id", length = 100)
    private String redactionJobId;

    @Column(name = "judge_verdict", length = 20)
    private String judgeVerdict;

    @Column(name = "judge_confidence")
    private Double judgeConfidence;
}
