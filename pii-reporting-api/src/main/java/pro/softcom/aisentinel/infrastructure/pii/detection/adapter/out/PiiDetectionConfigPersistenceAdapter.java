package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa.PiiDetectionConfigEntity;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa.PiiDetectionConfigJpaRepository;

/**
 * Persistence adapter for PII detection configuration.
 * Implements database access using Spring Data JPA for single-row configuration pattern.
 */
@Component
@Slf4j
public class PiiDetectionConfigPersistenceAdapter implements PiiDetectionConfigRepository {

    private static final Integer CONFIG_ID = 1;

    private final PiiDetectionConfigJpaRepository jpaRepository;

    private final PiiDetectionConfigRepository self;

    public PiiDetectionConfigPersistenceAdapter(PiiDetectionConfigJpaRepository jpaRepository,
                                                @Lazy PiiDetectionConfigRepository self) {
        this.jpaRepository = jpaRepository;
        this.self = self;
    }

    @Override
    @Transactional
    public PiiDetectionConfig findConfig() {
        log.debug("Retrieving PII detection configuration");
        
        return jpaRepository.findById(CONFIG_ID)
                .map(this::toDomain)
                .orElseGet(() -> {
                    log.warn("Configuration not found, creating default configuration");
                    return createDefaultConfig();
                });
    }

    @Override
    @Transactional
    public void updateConfig(PiiDetectionConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }
        
        log.info("Updating PII detection configuration: glinerEnabled={}, presidioEnabled={}, " +
                "regexEnabled={}, threshold={}, updatedBy={}", 
                config.isGlinerEnabled(), config.isPresidioEnabled(), 
                config.isRegexEnabled(), config.getDefaultThreshold(), 
                config.getUpdatedBy());
        
        PiiDetectionConfigEntity entity = toEntity(config);
        jpaRepository.save(entity);
        
        log.info("PII detection configuration updated successfully");
    }

    /**
     * Creates and persists default configuration.
     * Default: All detectors enabled, threshold 0.75
     */
    private PiiDetectionConfig createDefaultConfig() {
        log.info("Creating default PII detection configuration");
        
        PiiDetectionConfig defaultConfig = new PiiDetectionConfig(
                CONFIG_ID,
                true,  // glinerEnabled
                true,  // presidioEnabled
                true,  // regexEnabled
                new BigDecimal("0.75"),  // defaultThreshold
                LocalDateTime.now(),
                "system"
        );
        
        self.updateConfig(defaultConfig);
        return defaultConfig;
    }

    /**
     * Maps JPA entity to domain model.
     */
    private PiiDetectionConfig toDomain(PiiDetectionConfigEntity entity) {
        return new PiiDetectionConfig(
                entity.getId(),
                entity.getGlinerEnabled(),
                entity.getPresidioEnabled(),
                entity.getRegexEnabled(),
                entity.getDefaultThreshold(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy()
        );
    }

    /**
     * Maps domain model to JPA entity.
     */
    private PiiDetectionConfigEntity toEntity(PiiDetectionConfig config) {
        return new PiiDetectionConfigEntity(
                CONFIG_ID,  // Always use id=1 for single-row config
                config.isGlinerEnabled(),
                config.isPresidioEnabled(),
                config.isRegexEnabled(),
                config.getDefaultThreshold(),
                config.getUpdatedAt() != null ? config.getUpdatedAt() : LocalDateTime.now(),
                config.getUpdatedBy()
        );
    }
}
