package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceConnectionConfigRepository;
import pro.softcom.aisentinel.domain.confluence.ConfluenceConnectionSettings;
import pro.softcom.aisentinel.domain.confluence.ConfluenceDeploymentType;
import pro.softcom.aisentinel.domain.pii.security.EncryptionMetadata;
import pro.softcom.aisentinel.domain.pii.security.EncryptionService;

import java.util.concurrent.atomic.AtomicReference;

/**
 * DB-backed implementation of {@link ConfluenceConnectionConfig}.
 * Reads connection settings from the database and decrypts the API token.
 *
 * <p>Replaces the old {@code @ConfigurationProperties}-based config that read from
 * {@code application.yml} properties. API paths are hardcoded constants matching
 * the Confluence REST API v2 contract.
 */
@Component("confluenceConfig")
@Slf4j
public class DatabaseBackedConfluenceConnectionConfig implements ConfluenceConnectionConfig {

    private static final EncryptionMetadata TOKEN_METADATA = new EncryptionMetadata("CONFLUENCE_API_TOKEN", 0, 0);

    private final ConfluenceConnectionConfigRepository repository;
    private final EncryptionService encryptionService;

    private final AtomicReference<ConfluenceConnectionSettings> cachedSettings = new AtomicReference<>();

    public DatabaseBackedConfluenceConnectionConfig(ConfluenceConnectionConfigRepository repository,
                                                     EncryptionService encryptionService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        log.info("Database-backed Confluence connection config initialized");
    }

    @EventListener
    @Order(1)
    public void onConfigUpdated(ConfluenceConfigUpdatedEvent event) {
        log.info("Confluence configuration updated, invalidating cache");
        cachedSettings.set(null);
    }

    // --- Core connection ---

    @Override
    public String baseUrl() {
        return getSettings().baseUrl();
    }

    @Override
    public String username() {
        return getSettings().username();
    }

    @Override
    public String apiToken() {
        String encrypted = repository.getEncryptedApiToken();
        if (encrypted == null || encrypted.isBlank()) {
            return "";
        }
        if (!encryptionService.isEncrypted(encrypted)) {
            return encrypted;
        }
        return encryptionService.decrypt(encrypted, TOKEN_METADATA);
    }

    // --- Timeouts and retries ---

    @Override
    public int connectTimeout() {
        return getSettings().connectTimeout();
    }

    @Override
    public int readTimeout() {
        return getSettings().readTimeout();
    }

    @Override
    public int maxRetries() {
        return getSettings().maxRetries();
    }

    // --- Deployment type ---

    @Override
    public ConfluenceDeploymentType deploymentType() {
        return getSettings().deploymentType();
    }

    // --- Pagination ---

    @Override
    public int pagesLimit() {
        return getSettings().pagesLimit();
    }

    private ConfluenceConnectionSettings getSettings() {
        ConfluenceConnectionSettings settings = cachedSettings.get();
        if (settings == null) {
            settings = repository.findConfig();
            cachedSettings.set(settings);
        }
        return settings;
    }
}
