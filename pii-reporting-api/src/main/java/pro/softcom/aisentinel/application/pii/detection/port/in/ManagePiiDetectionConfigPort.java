package pro.softcom.aisentinel.application.pii.detection.port.in;

import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;

import java.math.BigDecimal;

/**
 * Port IN for managing PII detection configuration.
 * Defines use cases for retrieving and updating PII detection configuration.
 */
public interface ManagePiiDetectionConfigPort {

    /**
     * Retrieves the current PII detection configuration.
     *
     * @return The current configuration
     */
    PiiDetectionConfig getConfig();

    /**
     * Updates the PII detection configuration.
     *
     * @param command The update command containing new configuration values
     * @return The updated configuration
     * @throws IllegalArgumentException if command validation fails
     */
    PiiDetectionConfig updateConfig(UpdatePiiDetectionConfigCommand command);

    /**
     * Command to update PII detection configuration.
     *
     * @param presidioEnabled  Whether Presidio detector should be enabled
     * @param regexEnabled     Whether custom regex detector should be enabled
     * @param ministralEnabled    Whether the Ministral-PII detector should be enabled
     * @param ministralChunkSize  Sliding-window chunk size (characters) for the Ministral-PII detector
     * @param ministralOverlap    Sliding-window overlap (characters) for the Ministral-PII detector
     * @param defaultThreshold Default confidence threshold (0.0 to 1.0)
     * @param postfilterEnabled    Whether the deterministic format precision post-filter stage is enabled
     * @param lmStudioHost        Host of the LM Studio endpoint serving the Ministral-PII model
     * @param lmStudioPort        Port of the LM Studio endpoint serving the Ministral-PII model
     * @param lmStudioModel       LM Studio identifier of the Ministral-PII model to prompt (null = service default)
     * @param ministralConcurrency               Number of chunk prompts sent concurrently to LM Studio (1 = sequential)
     * @param ministralConcurrencyAuto           Whether the service auto-tunes the concurrency at startup
     * @param ministralConcurrencyTunedSignature The "host:port|model" signature the auto value was tuned for (null = never tuned)
     * @param updatedBy           User identifier who is updating the configuration
     */
    record UpdatePiiDetectionConfigCommand(
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
            String updatedBy
    ) {
    }
}