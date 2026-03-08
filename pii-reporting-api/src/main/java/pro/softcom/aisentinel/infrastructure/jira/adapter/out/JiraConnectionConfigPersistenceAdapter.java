package pro.softcom.aisentinel.infrastructure.jira.adapter.out;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pro.softcom.aisentinel.application.jira.port.out.JiraConnectionConfigRepository;
import pro.softcom.aisentinel.domain.jira.JiraConnectionSettings;
import pro.softcom.aisentinel.domain.jira.JiraDeploymentType;
import pro.softcom.aisentinel.infrastructure.jira.adapter.out.jpa.JiraConnectionConfigJpaRepository;
import pro.softcom.aisentinel.infrastructure.jira.adapter.out.jpa.entity.JiraConnectionConfigEntity;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence adapter for Jira connection configuration.
 * Implements database access using Spring Data JPA for single-row configuration pattern.
 */
@Component
@Slf4j
public class JiraConnectionConfigPersistenceAdapter implements JiraConnectionConfigRepository {

    private static final Integer CONFIG_ID = 1;

    private final JiraConnectionConfigJpaRepository jpaRepository;

    public JiraConnectionConfigPersistenceAdapter(JiraConnectionConfigJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JiraConnectionSettings> findSettings() {
        log.debug("Retrieving Jira connection configuration");
        return jpaRepository.findById(CONFIG_ID).map(this::toDomain);
    }

    @Override
    @Transactional
    public JiraConnectionSettings save(JiraConnectionSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }

        log.info("Saving Jira connection configuration: " +
                        "connectTimeout={}, readTimeout={}, maxRetries={}, issuesLimit={}, maxIssues={}, updatedBy={}",
                settings.connectTimeout(), settings.readTimeout(),
                settings.maxRetries(), settings.issuesLimit(), settings.maxIssues(),
                settings.updatedBy());

        JiraConnectionConfigEntity existing = jpaRepository.findById(CONFIG_ID).orElse(null);
        String existingToken = existing != null ? existing.getApiTokenEncrypted() : "";

        JiraConnectionConfigEntity entity = toEntity(settings, existingToken);
        JiraConnectionConfigEntity saved = jpaRepository.save(entity);

        log.info("Jira connection configuration saved successfully");
        return toDomain(saved);
    }

    @Override
    @Transactional
    public void saveEncryptedApiToken(String encryptedApiToken) {
        log.debug("Saving encrypted API token for Jira");
        JiraConnectionConfigEntity entity = jpaRepository.findById(CONFIG_ID)
                .orElseThrow(() -> new IllegalStateException("Jira configuration row not found"));
        entity.setApiTokenEncrypted(encryptedApiToken != null ? encryptedApiToken : "");
        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findDecryptedApiToken() {
        return jpaRepository.findById(CONFIG_ID)
                .map(JiraConnectionConfigEntity::getApiTokenEncrypted)
                .filter(token -> !token.isEmpty());
    }

    private JiraConnectionSettings toDomain(JiraConnectionConfigEntity entity) {
        return new JiraConnectionSettings(
                entity.getId(),
                entity.getBaseUrl(),
                entity.getEmail(),
                entity.getConnectTimeout(),
                entity.getReadTimeout(),
                entity.getMaxRetries(),
                entity.getIssuesLimit(),
                entity.getMaxIssues(),
                entity.getDeploymentType() != null ? entity.getDeploymentType() : JiraDeploymentType.CLOUD,
                entity.getUpdatedAt(),
                entity.getUpdatedBy()
        );
    }

    private JiraConnectionConfigEntity toEntity(JiraConnectionSettings settings, String encryptedToken) {
        return JiraConnectionConfigEntity.builder()
                .id(CONFIG_ID)
                .baseUrl(settings.baseUrl())
                .email(settings.email())
                .apiTokenEncrypted(encryptedToken != null ? encryptedToken : "")
                .connectTimeout(settings.connectTimeout())
                .readTimeout(settings.readTimeout())
                .maxRetries(settings.maxRetries())
                .issuesLimit(settings.issuesLimit())
                .maxIssues(settings.maxIssues())
                .deploymentType(settings.deploymentType() != null ? settings.deploymentType() : JiraDeploymentType.CLOUD)
                .updatedAt(settings.updatedAt() != null ? settings.updatedAt() : Instant.now())
                .updatedBy(settings.updatedBy())
                .build();
    }
}
