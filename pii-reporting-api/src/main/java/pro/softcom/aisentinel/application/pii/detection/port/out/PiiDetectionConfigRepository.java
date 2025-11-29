package pro.softcom.aisentinel.application.pii.detection.port.out;

import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;

/**
 * Port OUT for PII detection configuration persistence.
 * Defines repository operations for PII detection configuration.
 */
public interface PiiDetectionConfigRepository {

    /**
     * Retrieves the current PII detection configuration.
     * Since configuration is a singleton (single row), this always returns the config.
     *
     * @return The current PII detection configuration
     * @throws RuntimeException if configuration cannot be retrieved
     */
    PiiDetectionConfig findConfig();

    /**
     * Updates the PII detection configuration.
     * Since configuration is a singleton (single row), this updates the existing config.
     *
     * @param config The new configuration to persist
     * @throws IllegalArgumentException if config is invalid
     * @throws RuntimeException if update fails
     */
    void updateConfig(PiiDetectionConfig config);
}
