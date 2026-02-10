package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceConnectionConfigRepository;
import pro.softcom.aisentinel.domain.confluence.ConfluenceConnectionSettings;
import pro.softcom.aisentinel.domain.pii.security.EncryptionMetadata;
import pro.softcom.aisentinel.domain.pii.security.EncryptionService;

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

    // API path constants (previously in application.yml)
    private static final String CONTENT_PATH = "/content/";
    private static final String SEARCH_CONTENT_PATH = "/content/search";
    private static final String SPACE_PATH = "/space";
    private static final String ATTACHMENT_CHILD_SUFFIX = "/child/attachment";
    private static final String DEFAULT_PAGE_EXPANDS = "body.storage,version,metadata,ancestors";
    private static final String DEFAULT_SPACE_EXPANDS = "permissions,metadata";

    private final ConfluenceConnectionConfigRepository repository;
    private final EncryptionService encryptionService;

    private volatile ConfluenceConnectionSettings cachedSettings;

    public DatabaseBackedConfluenceConnectionConfig(ConfluenceConnectionConfigRepository repository,
                                                     EncryptionService encryptionService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        log.info("Database-backed Confluence connection config initialized");
    }

    @EventListener
    public void onConfigUpdated(ConfluenceConfigUpdatedEvent event) {
        log.info("Confluence configuration updated, invalidating cache");
        cachedSettings = null;
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

    // --- Timeouts, retries and proxy ---

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

    @Override
    public boolean enableProxy() {
        return false;
    }

    @Override
    public String proxyHost() {
        return null;
    }

    @Override
    public int proxyPort() {
        return 0;
    }

    @Override
    public String proxyUsername() {
        return null;
    }

    @Override
    public String proxyPassword() {
        return null;
    }

    // --- Pagination ---

    @Override
    public int pagesLimit() {
        return getSettings().pagesLimit();
    }

    @Override
    public int maxPages() {
        return getSettings().maxPages();
    }

    // --- API paths (hardcoded constants) ---

    @Override
    public String contentPath() {
        return CONTENT_PATH;
    }

    @Override
    public String searchContentPath() {
        return SEARCH_CONTENT_PATH;
    }

    @Override
    public String spacePath() {
        return SPACE_PATH;
    }

    @Override
    public String attachmentChildSuffix() {
        return ATTACHMENT_CHILD_SUFFIX;
    }

    @Override
    public String defaultPageExpands() {
        return DEFAULT_PAGE_EXPANDS;
    }

    @Override
    public String defaultSpaceExpands() {
        return DEFAULT_SPACE_EXPANDS;
    }

    private ConfluenceConnectionSettings getSettings() {
        ConfluenceConnectionSettings settings = cachedSettings;
        if (settings == null) {
            settings = repository.findConfig();
            cachedSettings = settings;
        }
        return settings;
    }
}
