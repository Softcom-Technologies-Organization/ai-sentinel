package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Configuration pour la connexion à Confluence.
 * Cette configuration est indépendante de toute instance Confluence spécifique.
 * Les valeurs des variables d'environnement déterminent l'instance Confluence ciblée.
 */
@ConfigurationProperties(prefix = "confluence")
public record ConfluenceConfig(
    String baseUrl,
    String username,
    String apiToken,
    String spaceKey,
    ConnectionSettings connectionSettings,
    PaginationSettings paginationSettings,
    ApiPaths apiPaths
) implements ConfluenceConnectionConfig {

    @ConstructorBinding
    public ConfluenceConfig {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be null or blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be null or blank");
        }
        if (apiToken == null || apiToken.isBlank()) {
            throw new IllegalArgumentException("apiToken must not be null or blank");
        }
    }

    @Override
    public int connectTimeout() {
        return connectionSettings.connectTimeout();
    }

    @Override
    public int readTimeout() {
        return connectionSettings.readTimeout();
    }

    @Override
    public int maxRetries() {
        return connectionSettings.maxRetries();
    }

    @Override
    public boolean enableProxy() {
        return connectionSettings.enableProxy();
    }

    @Override
    public String proxyHost() {
        return connectionSettings.proxySettings() != null 
            ? connectionSettings.proxySettings().host() 
            : null;
    }

    @Override
    public int proxyPort() {
        return connectionSettings.proxySettings() != null 
            ? connectionSettings.proxySettings().port() 
            : 0;
    }

    @Override
    public String proxyUsername() {
        return connectionSettings.proxySettings() != null 
            ? connectionSettings.proxySettings().username() 
            : null;
    }

    @Override
    public String proxyPassword() {
        return connectionSettings.proxySettings() != null 
            ? connectionSettings.proxySettings().password() 
            : null;
    }

    @Override
    public int pagesLimit() {
        return paginationSettings.pagesLimit();
    }

    @Override
    public int maxPages() {
        return paginationSettings.maxPages();
    }

    @Override
    public String contentPath() {
        return apiPaths.contentPath();
    }

    @Override
    public String searchContentPath() {
        return apiPaths.searchContentPath();
    }

    @Override
    public String spacePath() {
        return apiPaths.spacePath();
    }

    @Override
    public String attachmentChildSuffix() {
        return apiPaths.attachmentChildSuffix();
    }

    @Override
    public String defaultPageExpands() {
        return apiPaths.defaultPageExpands();
    }

    @Override
    public String defaultSpaceExpands() {
        return apiPaths.defaultSpaceExpands();
    }

    @Override
    public boolean isValid() {
        return baseUrl != null && !baseUrl.isBlank()
            && username != null && !username.isBlank()
            && apiToken != null && !apiToken.isBlank()
            && spaceKey != null && !spaceKey.isBlank();
    }

    @Override
    public String getRestApiUrl() {
        return baseUrl.endsWith("/")
            ? baseUrl + "rest/api"
            : baseUrl + "/rest/api";
    }

    /**
     * Paramètres de connexion HTTP.
     */
    public record ConnectionSettings(
        int connectTimeout,
        int readTimeout,
        int maxRetries,
        boolean enableProxy,
        ProxySettings proxySettings
    ) {
        public ConnectionSettings {
            if (connectTimeout <= 0) {
                connectTimeout = 30000;
            }
            if (readTimeout <= 0) {
                readTimeout = 60000;
            }
            if (maxRetries < 0) {
                maxRetries = 3;
            }
        }
    }

    /**
     * Paramètres de pagination pour les requêtes API.
     */
    public record PaginationSettings(
        int pagesLimit,
        int maxPages
    ) {
        public PaginationSettings {
            if (pagesLimit <= 0) {
                pagesLimit = 50;
            }
            if (maxPages <= 0) {
                maxPages = 100;
            }
        }
    }

    /**
     * Chemins des endpoints de l'API Confluence.
     */
    public record ApiPaths(
        String contentPath,
        String searchContentPath,
        String spacePath,
        String attachmentChildSuffix,
        String defaultPageExpands,
        String defaultSpaceExpands
    ) {}

    /**
     * Paramètres de configuration du proxy HTTP.
     */
    public record ProxySettings(
        String host,
        int port,
        String username,
        String password
    ) {}
}
