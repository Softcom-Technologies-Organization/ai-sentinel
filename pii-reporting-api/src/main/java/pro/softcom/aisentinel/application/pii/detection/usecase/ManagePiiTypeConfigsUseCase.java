package pro.softcom.aisentinel.application.pii.detection.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiTypeConfigsPort;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiTypeConfigRepository;
import pro.softcom.aisentinel.domain.pii.detection.PiiTypeConfig;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Use case implementation for managing PII type-specific configurations.
 * <p>
 * Business rules:
 * - Each PII type + detector combination is unique
 * - Threshold must be between 0.0 and 1.0
 * - Detector must be GLINER, PRESIDIO, or REGEX
 * - Updates are transactional
 */
@Service
@Transactional
public class ManagePiiTypeConfigsUseCase implements ManagePiiTypeConfigsPort {

    private final PiiTypeConfigRepository repository;

    public ManagePiiTypeConfigsUseCase(PiiTypeConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PiiTypeConfig> getAllConfigs() {
        return repository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PiiTypeConfig> getConfigsByDetector(String detector) {
        validateDetector(detector);
        return repository.findByDetector(detector);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<PiiTypeConfig>> getConfigsByCategory() {
        List<PiiTypeConfig> allConfigs = repository.findAll();
        return allConfigs.stream()
                .collect(Collectors.groupingBy(
                        config -> config.getCategory() != null ? config.getCategory() : "Other"
                ));
    }

    @Override
    public PiiTypeConfig updateConfig(
            String piiType,
            String detector,
            boolean enabled,
            double threshold,
            String updatedBy
    ) {
        validateDetector(detector);
        validateThreshold(threshold);

        PiiTypeConfig existing = repository.findByPiiTypeAndDetector(piiType, detector)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Configuration not found for PII type: " + piiType + " and detector: " + detector
                ));

        PiiTypeConfig updated = existing.withUpdate(enabled, threshold, updatedBy);
        return repository.save(updated);
    }

    @Override
    public List<PiiTypeConfig> bulkUpdate(List<PiiTypeConfigUpdate> updates, String updatedBy) {
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("Updates list cannot be null or empty");
        }

        // Validate all updates before applying any
        for (PiiTypeConfigUpdate update : updates) {
            validateDetector(update.detector());
            validateThreshold(update.threshold());
        }

        // Apply all updates
        List<PiiTypeConfig> updatedConfigs = updates.stream()
                .map(update -> {
                    PiiTypeConfig existing = repository.findByPiiTypeAndDetector(
                            update.piiType(),
                            update.detector()
                    ).orElseThrow(() -> new IllegalArgumentException(
                            "Configuration not found for PII type: " + update.piiType() +
                                    " and detector: " + update.detector()
                    ));

                    return existing.withUpdate(
                            update.enabled(),
                            update.threshold(),
                            updatedBy
                    );
                })
                .toList();

        return repository.saveAll(updatedConfigs);
    }

    private void validateDetector(String detector) {
        if (detector == null) {
            throw new IllegalArgumentException("Detector cannot be null");
        }
        if (!detector.equals("GLINER") && !detector.equals("PRESIDIO") && !detector.equals("REGEX")) {
            throw new IllegalArgumentException(
                    "Detector must be one of: GLINER, PRESIDIO, REGEX. Got: " + detector
            );
        }
    }

    private void validateThreshold(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException(
                    "Threshold must be between 0.0 and 1.0. Got: " + threshold
            );
        }
    }
}
