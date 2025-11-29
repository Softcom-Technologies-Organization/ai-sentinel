package pro.softcom.aisentinel.application.pii.detection.port.out;

import pro.softcom.aisentinel.domain.pii.detection.PiiTypeConfig;

import java.util.List;
import java.util.Optional;

/**
 * Port OUT for PII type configuration persistence.
 * <p>
 * Defines the contract for storing and retrieving PII type configurations.
 */
public interface PiiTypeConfigRepository {

    /**
     * Finds all PII type configurations.
     *
     * @return list of all configurations
     */
    List<PiiTypeConfig> findAll();

    /**
     * Finds all configurations for a specific detector.
     *
     * @param detector the detector name
     * @return list of configurations for the detector
     */
    List<PiiTypeConfig> findByDetector(String detector);

    /**
     * Finds configuration for a specific PII type and detector combination.
     *
     * @param piiType  the PII type identifier
     * @param detector the detector name
     * @return optional containing the configuration if found
     */
    Optional<PiiTypeConfig> findByPiiTypeAndDetector(String piiType, String detector);

    /**
     * Saves a single PII type configuration.
     *
     * @param config the configuration to save
     * @return the saved configuration
     */
    PiiTypeConfig save(PiiTypeConfig config);

    /**
     * Saves multiple PII type configurations.
     *
     * @param configs list of configurations to save
     * @return list of saved configurations
     */
    List<PiiTypeConfig> saveAll(List<PiiTypeConfig> configs);

    /**
     * Checks if configurations exist in database.
     * Used to determine if default data needs to be initialized.
     *
     * @return true if at least one configuration exists
     */
    boolean exists();
}
