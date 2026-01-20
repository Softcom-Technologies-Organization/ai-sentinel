package pro.softcom.aisentinel.application.pii.detection.port.in;

import pro.softcom.aisentinel.domain.pii.detection.PiiTypeConfig;

import java.util.List;
import java.util.Map;

/**
 * Port IN for managing PII type-specific configurations.
 * <p>
 * Allows clients to retrieve and update configuration for individual PII types
 * per detector (GLiNER, Presidio, Regex).
 */
public interface ManagePiiTypeConfigsPort {

    /**
     * Retrieves all PII type configurations.
     *
     * @return list of all PII type configurations
     */
    List<PiiTypeConfig> getAllConfigs();

    /**
     * Retrieves PII type configurations for a specific detector.
     *
     * @param detector the detector name (GLINER, PRESIDIO, or REGEX)
     * @return list of configurations for the specified detector
     * @throws IllegalArgumentException if detector is invalid
     */
    List<PiiTypeConfig> getConfigsByDetector(String detector);

    /**
     * Retrieves PII type configurations grouped by category.
     *
     * @return map of category to list of configurations
     */
    Map<String, List<PiiTypeConfig>> getConfigsByCategory();

    /**
     * Updates configuration for a specific PII type and detector.
     *
     * @param piiType     the PII type identifier
     * @param detector    the detector name
     * @param enabled     whether the PII type is enabled
     * @param threshold   the detection threshold (0.0-1.0)
     * @param updatedBy   the user making the update
     * @return the updated configuration
     * @throws IllegalArgumentException if parameters are invalid
     */
    PiiTypeConfig updateConfig(String piiType, String detector, boolean enabled, double threshold, String updatedBy);

    /**
     * Bulk update of multiple PII type configurations.
     *
     * @param updates   list of configuration updates
     * @param updatedBy the user making the updates
     * @return list of updated configurations
     * @throws IllegalArgumentException if any update is invalid
     */
    List<PiiTypeConfig> bulkUpdate(List<PiiTypeConfigUpdate> updates, String updatedBy);

    /**
     * Represents a single configuration update.
     */
    record PiiTypeConfigUpdate(
            String piiType,
            String detector,
            boolean enabled,
            double threshold
    ) {
    }
}
