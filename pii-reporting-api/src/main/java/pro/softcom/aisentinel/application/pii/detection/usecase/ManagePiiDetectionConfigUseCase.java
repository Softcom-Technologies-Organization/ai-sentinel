package pro.softcom.aisentinel.application.pii.detection.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiDetectionConfigPort;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;

import java.time.LocalDateTime;

/**
 * Use case for managing PII detection configuration.
 * Handles retrieval and updates of detection configuration.
 */
public class ManagePiiDetectionConfigUseCase implements ManagePiiDetectionConfigPort {

    private static final Logger log = LoggerFactory.getLogger(ManagePiiDetectionConfigUseCase.class);
    private static final Integer CONFIG_ID = 1;

    private final PiiDetectionConfigRepository repository;

    public ManagePiiDetectionConfigUseCase(PiiDetectionConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public PiiDetectionConfig getConfig() {
        log.debug("Retrieving PII detection configuration");
        return repository.findConfig();
    }

    @Override
    public PiiDetectionConfig updateConfig(UpdatePiiDetectionConfigCommand command) {
        log.info("Updating PII detection configuration: gliner={}, presidio={}, regex={}, threshold={}, nbOfLabelByPass={}",
                command.glinerEnabled(), command.presidioEnabled(), command.regexEnabled(),
                command.defaultThreshold(), command.nbOfLabelByPass());

        PiiDetectionConfig newConfig = new PiiDetectionConfig(
                CONFIG_ID,
                command.glinerEnabled(),
                command.presidioEnabled(),
                command.regexEnabled(),
                command.defaultThreshold(),
                command.nbOfLabelByPass(),
                LocalDateTime.now(),
                command.updatedBy()
        );

        repository.updateConfig(newConfig);

        log.info("PII detection configuration updated successfully by user: {}", command.updatedBy());
        return newConfig;
    }
}