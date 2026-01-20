package pro.softcom.aisentinel.application.pii.detection.port.out;

import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiTypeConfigsPort.PiiTypeConfigUpdate;
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
     * Updates a PII type configuration atomically.
     * <p>
     * This method performs the read-modify-write operation within a single transaction
     * to prevent race conditions and lost updates.
     *
     * @param piiType   the PII type identifier
     * @param detector  the detector name
     * @param enabled   whether the configuration is enabled
     * @param threshold the detection threshold
     * @param updatedBy the user performing the update
     * @return the updated configuration
     * @throws IllegalArgumentException if configuration not found
     */
    PiiTypeConfig updateAtomically(String piiType, String detector, boolean enabled, double threshold, String updatedBy);

    /**
     * Updates multiple PII type configurations atomically.
     * <p>
     * All updates are performed within a single transaction to ensure consistency
     * and prevent race conditions.
     *
     * @param updates   list of updates to apply
     * @param updatedBy the user performing the updates
     * @return list of updated configurations
     * @throws IllegalArgumentException if any configuration not found
     */
    List<PiiTypeConfig> bulkUpdateAtomically(List<PiiTypeConfigUpdate> updates, String updatedBy);

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
