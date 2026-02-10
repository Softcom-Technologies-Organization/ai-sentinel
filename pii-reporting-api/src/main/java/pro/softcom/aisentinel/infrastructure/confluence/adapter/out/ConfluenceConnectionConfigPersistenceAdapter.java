package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceConnectionConfigRepository;
import pro.softcom.aisentinel.domain.confluence.ConfluenceConnectionSettings;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.jpa.ConfluenceConnectionConfigJpaRepository;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.jpa.entity.ConfluenceConnectionConfigEntity;

import java.time.LocalDateTime;

/**
 * Persistence adapter for Confluence connection configuration.
 * Implements database access using Spring Data JPA for single-row configuration pattern.
 */
@Component
@Slf4j
public class ConfluenceConnectionConfigPersistenceAdapter implements ConfluenceConnectionConfigRepository {

    private static final Integer CONFIG_ID = 1;

    private final ConfluenceConnectionConfigJpaRepository jpaRepository;

    public ConfluenceConnectionConfigPersistenceAdapter(ConfluenceConnectionConfigJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ConfluenceConnectionSettings findConfig() {
        log.debug("Retrieving Confluence connection configuration");

        return jpaRepository.findById(CONFIG_ID)
                .map(this::toDomain)
                .orElseGet(() -> {
                    log.warn("Configuration not found, returning default configuration");
                    return createDefaultSettings();
                });
    }

    @Override
    @Transactional
    public void updateConfig(ConfluenceConnectionSettings settings, String encryptedToken) {
        if (settings == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }

        log.info("Updating Confluence connection configuration: baseUrl={}, username={}, " +
                        "connectTimeout={}, readTimeout={}, maxRetries={}, pagesLimit={}, maxPages={}, updatedBy={}",
                settings.baseUrl(), settings.username(),
                settings.connectTimeout(), settings.readTimeout(),
                settings.maxRetries(), settings.pagesLimit(), settings.maxPages(),
                settings.updatedBy());

        ConfluenceConnectionConfigEntity entity = toEntity(settings, encryptedToken);
        jpaRepository.save(entity);

        log.info("Confluence connection configuration updated successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public String getEncryptedApiToken() {
        return jpaRepository.findById(CONFIG_ID)
                .map(ConfluenceConnectionConfigEntity::getApiTokenEncrypted)
                .orElse("");
    }

    /**
     * Creates default settings (used when no configuration exists in DB).
     */
    private ConfluenceConnectionSettings createDefaultSettings() {
        return new ConfluenceConnectionSettings(
                CONFIG_ID,
                "https://confluence.example.com",
                "admin",
                30000,
                60000,
                3,
                50,
                100,
                LocalDateTime.now(),
                "system"
        );
    }

    /**
     * Maps JPA entity to domain model.
     */
    private ConfluenceConnectionSettings toDomain(ConfluenceConnectionConfigEntity entity) {
        return new ConfluenceConnectionSettings(
                entity.getId(),
                entity.getBaseUrl(),
                entity.getUsername(),
                entity.getConnectTimeout(),
                entity.getReadTimeout(),
                entity.getMaxRetries(),
                entity.getPagesLimit(),
                entity.getMaxPages(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy()
        );
    }

    /**
     * Maps domain model to JPA entity.
     */
    private ConfluenceConnectionConfigEntity toEntity(ConfluenceConnectionSettings settings, String encryptedToken) {
        return ConfluenceConnectionConfigEntity.builder()
                .id(CONFIG_ID)
                .baseUrl(settings.baseUrl())
                .username(settings.username())
                .apiTokenEncrypted(encryptedToken != null ? encryptedToken : "")
                .connectTimeout(settings.connectTimeout())
                .readTimeout(settings.readTimeout())
                .maxRetries(settings.maxRetries())
                .pagesLimit(settings.pagesLimit())
                .maxPages(settings.maxPages())
                .updatedAt(settings.updatedAt() != null ? settings.updatedAt() : LocalDateTime.now())
                .updatedBy(settings.updatedBy())
                .build();
    }
}
