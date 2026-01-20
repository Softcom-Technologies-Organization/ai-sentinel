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
     * @param glinerEnabled    Whether GLiNER detector should be enabled
     * @param presidioEnabled  Whether Presidio detector should be enabled
     * @param regexEnabled     Whether custom regex detector should be enabled
     * @param defaultThreshold Default confidence threshold (0.0 to 1.0)
     * @param updatedBy        User identifier who is updating the configuration
     */
    record UpdatePiiDetectionConfigCommand(
            boolean glinerEnabled,
            boolean presidioEnabled,
            boolean regexEnabled,
            BigDecimal defaultThreshold,
            Integer nbOfLabelByPass,
            String updatedBy
    ) {
    }
}