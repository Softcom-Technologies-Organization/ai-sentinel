package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for updating PII detection configuration via REST API.
 *
 * <p>Business purpose: Allows clients to modify detector enable/disable states
 * and confidence thresholds used by the PII detection service.
 *
 * <p>Validation: Ensures at least one detector is enabled and threshold is within valid range.
 *
 * @param presidioEnabled  Whether Presidio detector should be enabled
 * @param regexEnabled     Whether custom regex detector should be enabled
 * @param ministralEnabled    Whether the Ministral-PII detector should be enabled
 * @param ministralChunkSize  Sliding-window chunk size (tokens) for the Ministral-PII detector (256-4096)
 * @param ministralOverlap    Sliding-window overlap (tokens) for the Ministral-PII detector (0-512, less than the chunk size)
 * @param defaultThreshold Default confidence threshold (0.0 to 1.0)
 * @param postfilterEnabled    Whether the deterministic format precision post-filter stage is enabled.
 *                            Optional in the payload: when omitted, defaults to {@code false}.
 * @param lmStudioHost        Host of the LM Studio endpoint serving the Ministral-PII model
 * @param lmStudioPort        Port of the LM Studio endpoint serving the Ministral-PII model (1-65535)
 * @param lmStudioModel       LM Studio identifier of the Ministral-PII model the detector prompts.
 *                            Optional in the payload: {@code null} means "detector service default".
 * @param lmStudioJudgeModel  Identifier of the chat model used by the LLM judge; a manual identifier is accepted.
 * @param ministralConcurrency               Number of chunk prompts sent concurrently to LM Studio (1-16, 1 = sequential)
 * @param ministralConcurrencyAuto           Whether the service auto-tunes the concurrency at startup
 * @param ministralConcurrencyTunedSignature The "host:port|model" signature the auto value was tuned for.
 *                                           Optional in the payload: {@code null} means "never tuned / re-tune at next startup".
 */
public record UpdatePiiDetectionConfigRequestDto(
    @JsonProperty("presidioEnabled")
    @NotNull(message = "presidioEnabled is required")
    Boolean presidioEnabled,

    @JsonProperty("regexEnabled")
    @NotNull(message = "regexEnabled is required")
    Boolean regexEnabled,

    @JsonProperty("ministralEnabled")
    @NotNull(message = "ministralEnabled is required")
    Boolean ministralEnabled,

    @JsonProperty("ministralChunkSize")
    @NotNull(message = "ministralChunkSize is required")
    @Min(value = 256, message = "ministralChunkSize must be at least 256")
    @Max(value = 4096, message = "ministralChunkSize must be at most 4096")
    Integer ministralChunkSize,

    @JsonProperty("ministralOverlap")
    @NotNull(message = "ministralOverlap is required")
    @Min(value = 0, message = "ministralOverlap must be at least 0")
    @Max(value = 512, message = "ministralOverlap must be at most 512")
    Integer ministralOverlap,

    @JsonProperty("defaultThreshold")
    @NotNull(message = "defaultThreshold is required")
    @DecimalMin(value = "0.0", message = "Default threshold must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Default threshold must be at most 1.0")
    BigDecimal defaultThreshold,

    @JsonProperty("postfilterEnabled")
    Boolean postfilterEnabled,

    @JsonProperty("lmStudioHost")
    @NotBlank(message = "lmStudioHost is required")
    String lmStudioHost,

    @JsonProperty("lmStudioPort")
    @NotNull(message = "lmStudioPort is required")
    @Min(value = 1, message = "lmStudioPort must be at least 1")
    @Max(value = 65535, message = "lmStudioPort must be at most 65535")
    Integer lmStudioPort,

    @JsonProperty("lmStudioModel")
    @Size(max = 255, message = "lmStudioModel must be at most 255 characters")
    String lmStudioModel,

    @JsonProperty("lmStudioJudgeModel")
    @Size(max = 255, message = "lmStudioJudgeModel must be at most 255 characters")
    String lmStudioJudgeModel,

    @JsonProperty("ministralConcurrency")
    @NotNull(message = "ministralConcurrency is required")
    @Min(value = 1, message = "ministralConcurrency must be at least 1")
    @Max(value = 20, message = "ministralConcurrency must be at most 20")
    Integer ministralConcurrency,

    @JsonProperty("ministralConcurrencyAuto")
    @NotNull(message = "ministralConcurrencyAuto is required")
    Boolean ministralConcurrencyAuto,

    @JsonProperty("ministralConcurrencyTunedSignature")
    String ministralConcurrencyTunedSignature,

    Boolean trioEnabled,
    Boolean bardsEnabled,
    Boolean cbxEnabled,
    Boolean ettinEnabled,
    @Pattern(regexp = "LM_STUDIO|OMLX|STRATA", message = "Unsupported judge provider")
    String judgeProvider,
    @Size(max = 255, message = "judgeHost must be at most 255 characters")
    String judgeHost,
    @Min(value = 1, message = "judgePort must be at least 1")
    @Max(value = 65535, message = "judgePort must be at most 65535")
    Integer judgePort,
    List<@NotBlank String> judgeEnabledCategories
) {
    public UpdatePiiDetectionConfigRequestDto {
        trioEnabled = Boolean.TRUE.equals(trioEnabled);
        bardsEnabled = !Boolean.FALSE.equals(bardsEnabled);
        cbxEnabled = !Boolean.FALSE.equals(cbxEnabled);
        ettinEnabled = !Boolean.FALSE.equals(ettinEnabled);
    }

    @AssertTrue(message = "Judge host and port are required when a provider is selected")
    public boolean isJudgeEndpointConfigured() {
        return judgeProvider == null || (judgeHost != null && !judgeHost.isBlank() && judgePort != null);
    }

    /**
     * Cross-field rule: the Ministral overlap must be strictly smaller than the
     * chunk size. Returns {@code true} when either value is {@code null} so the
     * {@link NotNull} constraints report the missing field instead.
     */
    @AssertTrue(message = "ministralOverlap must be less than ministralChunkSize")
    public boolean isMinistralOverlapLessThanChunkSize() {
        if (ministralOverlap == null || ministralChunkSize == null) {
            return true;
        }
        return ministralOverlap < ministralChunkSize;
    }

    /**
     * Returns the {@code ministralChunkSize} value with a {@code 2048} default
     * when the client omits the field.
     */
    public int ministralChunkSizeOrDefault() {
        return ministralChunkSize != null ? ministralChunkSize : 2048;
    }

    /**
     * Returns the {@code ministralOverlap} value with a {@code 410} default
     * when the client omits the field.
     */
    public int ministralOverlapOrDefault() {
        return ministralOverlap != null ? ministralOverlap : 410;
    }

    /**
     * Returns the {@code postfilterEnabled} flag value with a {@code false} default
     * when the client omits the field. Keeps the rollout zero-effect.
     */
    public boolean postfilterEnabledOrDefault() {
        return postfilterEnabled != null && postfilterEnabled;
    }

    @AssertTrue(message = "At least one detector must be enabled")
    public boolean isAtLeastOneDetectorEnabled() {
        return Boolean.TRUE.equals(presidioEnabled) || Boolean.TRUE.equals(regexEnabled)
                || Boolean.TRUE.equals(ministralEnabled) || trioEnabled;
    }

    @AssertTrue(message = "Trio requires at least one enabled model")
    public boolean isTrioConfigurationValid() {
        return !trioEnabled || bardsEnabled || cbxEnabled || ettinEnabled;
    }
}
