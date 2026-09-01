package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

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
    @Column(name = "presidio_enabled", nullable = false)
    private Boolean presidioEnabled;

    @NotNull
    @Column(name = "regex_enabled", nullable = false)
    private Boolean regexEnabled;

    /**
     * Activates the Ministral-PII detector (specialised LLM source). Defaults to
     * {@code false} for an explicit operator opt-in.
     */
    @NotNull
    @Column(name = "ministral_enabled", nullable = false)
    private Boolean ministralEnabled;

    /**
     * Sliding-window chunk size (characters) used by the Ministral-PII detector.
     */
    @NotNull
    @Column(name = "ministral_chunk_size", nullable = false)
    private Integer ministralChunkSize;

    /**
     * Sliding-window overlap (characters) used by the Ministral-PII detector.
     */
    @NotNull
    @Column(name = "ministral_overlap", nullable = false)
    private Integer ministralOverlap;

    @NotNull
    @DecimalMin(value = "0.0", message = "Default threshold must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Default threshold must be at most 1.0")
    @Column(name = "default_threshold", nullable = false, precision = 3, scale = 2)
    private BigDecimal defaultThreshold;

    /**
     * Activates the deterministic format precision post-filter (IP/MAC/IBAN
     * checksum) that runs after detection. Defaults to {@code false} for a
     * zero-effect rollout.
     */
    @NotNull
    @Column(name = "postfilter_enabled", nullable = false)
    private Boolean postfilterEnabled;

    /**
     * Host of the OpenAI-compatible LM Studio endpoint serving the Ministral-PII
     * model. Defaults to {@code localhost}.
     */
    @NotNull
    @Column(name = "lm_studio_host", nullable = false)
    private String lmStudioHost;

    /**
     * Port of the OpenAI-compatible LM Studio endpoint serving the Ministral-PII
     * model. Defaults to {@code 1234}.
     */
    @NotNull
    @Column(name = "lm_studio_port", nullable = false)
    private Integer lmStudioPort;

    /**
     * Number of chunk prompts the Ministral-PII detector sends concurrently to
     * the LM Studio endpoint (1 = sequential). Defaults to {@code 1}.
     */
    @NotNull
    @Column(name = "ministral_concurrency", nullable = false)
    private Integer ministralConcurrency;

    /**
     * When {@code true}, the service auto-tunes {@code ministralConcurrency} at
     * startup; when {@code false}, the value is operator-pinned. Defaults to
     * {@code true}.
     */
    @NotNull
    @Column(name = "ministral_concurrency_auto", nullable = false)
    private Boolean ministralConcurrencyAuto;

    /**
     * The {@code "host:port|model"} signature the current auto-tuned concurrency
     * was measured for. {@code null} means "never tuned / re-tune at next
     * startup".
     */
    @Column(name = "ministral_concurrency_tuned_signature")
    private String ministralConcurrencyTunedSignature;

    /**
     * Set to {@code true} by the API to request an on-demand concurrency
     * benchmark; the detector service resets it once the run starts.
     */
    @NotNull
    @Builder.Default
    @Column(name = "concurrency_bench_requested", nullable = false)
    private Boolean concurrencyBenchRequested = false;

    /**
     * Lifecycle of the on-demand benchmark job, written by the detector
     * service: IDLE, PENDING, RUNNING, CANCEL_REQUESTED, CANCELLED, DONE or FAILED.
     */
    @NotNull
    @Builder.Default
    @Column(name = "concurrency_bench_status", nullable = false)
    private String concurrencyBenchStatus = "IDLE";

    /**
     * Benchmark job progress percentage (0..100), written by the detector
     * service.
     */
    @NotNull
    @Builder.Default
    @Column(name = "concurrency_bench_progress", nullable = false)
    private Integer concurrencyBenchProgress = 0;

    /**
     * Human-readable benchmark outcome or failure message ({@code null} when
     * idle).
     */
    @Column(name = "concurrency_bench_message")
    private String concurrencyBenchMessage;

    /**
     * Highest concurrency level the on-demand benchmark measures (2..20),
     * chosen by the operator when requesting the run.
     */
    @NotNull
    @Builder.Default
    @ColumnDefault("4")
    @Column(name = "concurrency_bench_max_concurrency", nullable = false)
    private Integer concurrencyBenchMaxConcurrency = 4;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
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