package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO representing PII detection configuration for REST API responses.
 *
 * <p>Business purpose: Provides clients with current detector enable/disable states
 * and confidence thresholds used by the PII detection service.
 *
 * @param presidioEnabled  Whether Presidio detector is enabled
 * @param regexEnabled     Whether custom regex detector is enabled
 * @param ministralEnabled    Whether the Ministral-PII detector is enabled
 * @param ministralChunkSize  Sliding-window chunk size (tokens) for the Ministral-PII detector
 * @param ministralOverlap    Sliding-window overlap (tokens) for the Ministral-PII detector
 * @param defaultThreshold Default confidence threshold (0.0 to 1.0)
 * @param postfilterEnabled    Whether the deterministic format precision post-filter stage is enabled
 * @param lmStudioHost        Host of the LM Studio endpoint serving the Ministral-PII model
 * @param lmStudioPort        Port of the LM Studio endpoint serving the Ministral-PII model
 * @param ministralConcurrency               Number of chunk prompts sent concurrently to LM Studio (1 = sequential)
 * @param ministralConcurrencyAuto           Whether the service auto-tunes the concurrency at startup
 * @param ministralConcurrencyTunedSignature The "host:port|model" signature the auto value was tuned for (null = never tuned)
 * @param updatedAt           Timestamp of last configuration update
 * @param updatedBy           User who last updated the configuration
 */
public record PiiDetectionConfigResponseDto(
    boolean presidioEnabled,
    boolean regexEnabled,
    boolean ministralEnabled,
    Integer ministralChunkSize,
    Integer ministralOverlap,
    BigDecimal defaultThreshold,
    boolean postfilterEnabled,
    String lmStudioHost,
    Integer lmStudioPort,
    String lmStudioModel,
    Integer ministralConcurrency,
    boolean ministralConcurrencyAuto,
    String ministralConcurrencyTunedSignature,
    LocalDateTime updatedAt,
    String updatedBy
) {
}