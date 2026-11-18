package pro.softcom.aisentinel.domain.pii.detection;

import pro.softcom.aisentinel.domain.pii.judge.JudgeEndpoint;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Domain model for PII detection configuration.
 * Represents the configuration settings for PII detection detectors and thresholds.
 * This is the single source of truth for detection configuration in the system.
 * Detector must be one of: PRESIDIO, REGEX, MINISTRAL, BARDS, CBX, ETTIN.
 *
 * <p>The {@code postfilterEnabled} flag activates the deterministic format
 * precision post-filter (IP/MAC/IBAN checksum) that runs after detection.
 */
public record PiiDetectionConfig(
        Integer id,
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
        String lmStudioJudgeModel,
        Integer ministralConcurrency,
        boolean ministralConcurrencyAuto,
        String ministralConcurrencyTunedSignature,
        LocalDateTime updatedAt,
        String updatedBy,
        boolean trioEnabled,
        boolean bardsEnabled,
        boolean cbxEnabled,
        boolean ettinEnabled,
        String judgeProvider,
        String judgeHost,
        Integer judgePort,
        List<String> judgeEnabledCategories) {

    private static final BigDecimal MIN_THRESHOLD = BigDecimal.ZERO;
    private static final BigDecimal MAX_THRESHOLD = BigDecimal.ONE;
    private static final int MIN_MINISTRAL_CHUNK_SIZE = 256;
    private static final int MAX_MINISTRAL_CHUNK_SIZE = 4096;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    private static final int MIN_MINISTRAL_CONCURRENCY = 1;
    private static final int MAX_MINISTRAL_CONCURRENCY = 20;

    /**
     * Compact constructor for validation.
     *
     * @throws IllegalArgumentException if threshold is out of range or other validation fails
     */
    public PiiDetectionConfig {
        JudgeEndpoint configuredJudge = new JudgeEndpoint(judgeHost, judgePort);
        judgeHost = configuredJudge.host();
        validateJudgeConfiguration(judgeProvider, configuredJudge);
        judgeEnabledCategories = judgeEnabledCategories == null ? null : List.copyOf(judgeEnabledCategories);
        lmStudioJudgeModel = lmStudioJudgeModel == null || lmStudioJudgeModel.isBlank()
                ? "qwen3.8-flash-next-iq3_s" : lmStudioJudgeModel;
        validateThreshold(defaultThreshold);
        validateDetectorActivation(
                presidioEnabled || regexEnabled || ministralEnabled || trioEnabled,
                !trioEnabled || bardsEnabled || cbxEnabled || ettinEnabled);

        if (ministralChunkSize == null
                || ministralChunkSize < MIN_MINISTRAL_CHUNK_SIZE
                || ministralChunkSize > MAX_MINISTRAL_CHUNK_SIZE) {
            throw new IllegalArgumentException(
                    "Ministral chunk size must be between " + MIN_MINISTRAL_CHUNK_SIZE
                            + " and " + MAX_MINISTRAL_CHUNK_SIZE);
        }

        if (ministralOverlap == null
                || ministralOverlap < 0
                || ministralOverlap >= ministralChunkSize) {
            throw new IllegalArgumentException(
                    "Ministral overlap must be between 0 (inclusive) and the chunk size (exclusive)");
        }

        if (lmStudioHost == null || lmStudioHost.isBlank()) {
            throw new IllegalArgumentException("LM Studio host cannot be blank");
        }

        if (lmStudioPort == null || lmStudioPort < MIN_PORT || lmStudioPort > MAX_PORT) {
            throw new IllegalArgumentException(
                    "LM Studio port must be between " + MIN_PORT + " and " + MAX_PORT);
        }

        if (ministralConcurrency == null
                || ministralConcurrency < MIN_MINISTRAL_CONCURRENCY
                || ministralConcurrency > MAX_MINISTRAL_CONCURRENCY) {
            throw new IllegalArgumentException(
                    "Ministral concurrency must be between " + MIN_MINISTRAL_CONCURRENCY
                            + " and " + MAX_MINISTRAL_CONCURRENCY);
        }
    }

    public JudgeEndpoint judgeEndpoint(JudgeEndpoint fallback) {
        return new JudgeEndpoint(judgeHost, judgePort)
                .resolve(fallback.host(), fallback.port())
                .resolve(lmStudioHost, lmStudioPort);
    }

    private static void validateJudgeConfiguration(String provider, JudgeEndpoint endpoint) {
        if (provider == null) {
            return;
        }
        if (!Set.of("LM_STUDIO", "OMLX", "STRATA").contains(provider)) {
            throw new IllegalArgumentException("Unsupported judge provider");
        }
        if (endpoint.host() == null || endpoint.port() == null) {
            throw new IllegalArgumentException("Judge host and port are required when a provider is selected");
        }
    }

    private static void validateThreshold(BigDecimal threshold) {
        if (threshold == null) {
            throw new IllegalArgumentException("Default threshold cannot be null");
        }
        if (threshold.compareTo(MIN_THRESHOLD) < 0) {
            throw new IllegalArgumentException(
                    "Default threshold must be greater than or equal to " + MIN_THRESHOLD);
        }
        if (threshold.compareTo(MAX_THRESHOLD) > 0) {
            throw new IllegalArgumentException(
                    "Default threshold must be less than or equal to " + MAX_THRESHOLD);
        }
    }

    private static void validateDetectorActivation(boolean hasEnabledDetector, boolean hasValidTrio) {
        if (!hasValidTrio) {
            throw new IllegalArgumentException("Trio requires at least one enabled model");
        }
        if (!hasEnabledDetector) {
            throw new IllegalArgumentException("At least one detector must be enabled");
        }
    }

}
