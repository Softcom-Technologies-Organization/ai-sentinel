package pro.softcom.aisentinel.infrastructure.pii.judge.adapter.out.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA entity mapped to pii_judge_job table, the journal of LLM judge runs. The
 * one-RUNNING-job-per-space invariant is a partial unique index that JPA cannot express; it
 * lives in init-scripts/018-pii-llm-judge.sql.
 */
@Getter
@Setter
@Entity
@Table(name = "pii_judge_job", indexes = {
        @Index(name = "idx_pii_judge_job_space_status", columnList = "space_key,status")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeJobEntity {

    @Id
    @Column(name = "id", nullable = false, length = 100)
    private String id;

    @Column(name = "space_key", nullable = false, length = 50)
    private String spaceKey;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "model", length = 200)
    private String model;

    @Column(name = "apply_mode", nullable = false, length = 20)
    private String applyMode;

    @Column(name = "confidence_threshold", nullable = false)
    private double confidenceThreshold;

    @Column(name = "processed", nullable = false)
    private int processed;

    @Column(name = "total", nullable = false)
    private int total;

    @Column(name = "false_positives", nullable = false)
    private int falsePositives;

    @Column(name = "errors", nullable = false)
    private int errors;

    @Column(name = "actor", nullable = false)
    private String actor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
