package pro.softcom.aisentinel.application.pii.detection.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiDetectionConfigPort;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;

import java.time.LocalDateTime;
import java.time.ZoneId;

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
        log.info("Updating PII detection configuration: presidio={}, regex={}, ministral={}, threshold={}, postfilterEnabled={}",
                command.presidioEnabled(), command.regexEnabled(), command.ministralEnabled(),
                command.defaultThreshold(), command.postfilterEnabled());

        PiiDetectionConfig newConfig = new PiiDetectionConfig(
                CONFIG_ID,
                command.presidioEnabled(),
                command.regexEnabled(),
                command.ministralEnabled(),
                command.ministralChunkSize(),
                command.ministralOverlap(),
                command.defaultThreshold(),
                command.postfilterEnabled(),
                command.lmStudioHost(),
                command.lmStudioPort(),
                command.lmStudioModel(),
                command.ministralConcurrency(),
                command.ministralConcurrencyAuto(),
                command.ministralConcurrencyTunedSignature(),
                LocalDateTime.now(ZoneId.systemDefault()),
                command.updatedBy()
        );

        repository.updateConfig(newConfig);

        log.info("PII detection configuration updated successfully by user: {}", command.updatedBy());
        return newConfig;
    }
}