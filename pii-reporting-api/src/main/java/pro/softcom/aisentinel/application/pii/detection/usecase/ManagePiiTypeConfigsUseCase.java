package pro.softcom.aisentinel.application.pii.detection.usecase;

import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiTypeConfigsPort;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiTypeConfigRepository;
import pro.softcom.aisentinel.domain.pii.detection.PiiTypeConfig;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
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
public class ManagePiiTypeConfigsUseCase implements ManagePiiTypeConfigsPort {

    private static final Pattern PII_TYPE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,98}$");

    private final PiiTypeConfigRepository repository;

    public ManagePiiTypeConfigsUseCase(PiiTypeConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public PiiTypeConfig createConfig(CreatePiiTypeConfigCommand command) {
        validatePiiTypeFormat(command.piiType());
        validateDetector(command.detector());
        validateThreshold(command.threshold());

        if ("GLINER".equals(command.detector()) && (command.detectorLabel() == null || command.detectorLabel().isBlank())) {
            throw new IllegalArgumentException("Detector label is required for GLINER detector");
        }

        repository.findByPiiTypeAndDetector(command.piiType(), command.detector()).ifPresent(_ -> {
            throw new IllegalArgumentException(
                    "Configuration already exists for PII type: " + command.piiType() + " and detector: " + command.detector()
            );
        });

        PiiTypeConfig config = PiiTypeConfig.builder()
                .piiType(command.piiType())
                .detector(command.detector())
                .enabled(command.enabled())
                .threshold(command.threshold())
                .category(command.category())
                .detectorLabel(command.detectorLabel())
                .countryCode(command.countryCode())
                .custom(true)
                .severity(command.severity() != null ? command.severity() : "LOW")
                .updatedBy(command.createdBy())
                .build();

        return repository.save(config);
    }

    @Override
    public List<PiiTypeConfig> getAllConfigs() {
        return repository.findAll();
    }

    @Override
    public List<PiiTypeConfig> getConfigsByDetector(String detector) {
        validateDetector(detector);
        return repository.findByDetector(detector);
    }

    @Override
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

        return repository.updateAtomically(piiType, detector, enabled, threshold, updatedBy);
    }

    @Override
    public void deleteConfig(String piiType, String detector) {
        PiiTypeConfig existing = repository.findByPiiTypeAndDetector(piiType, detector)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Configuration not found for PII type: " + piiType + " and detector: " + detector
                ));

        if (!existing.isCustom()) {
            throw new IllegalStateException(
                    "Cannot delete system-defined PII type: " + piiType + " for detector: " + detector
            );
        }

        repository.deleteByPiiTypeAndDetector(piiType, detector);
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

        return repository.bulkUpdateAtomically(updates, updatedBy);
    }

    private void validatePiiTypeFormat(String piiType) {
        if (piiType == null || !PII_TYPE_PATTERN.matcher(piiType).matches()) {
            throw new IllegalArgumentException(
                    "PII type must be UPPER_SNAKE_CASE (2-99 chars, starts with letter). Got: " + piiType
            );
        }
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
