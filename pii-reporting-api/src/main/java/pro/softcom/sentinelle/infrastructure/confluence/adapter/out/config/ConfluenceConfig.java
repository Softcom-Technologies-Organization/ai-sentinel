package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "confluence")
@Slf4j
public record ConfluenceConfig(
    String baseUrl,
    String username,
    String apiToken,
    ConnectionSettings connectionSettings,
    PaginationSettings paginationSettings,
    ApiPaths apiPaths,
    CacheSettings cache,
    PollingSettings polling
) implements ConfluenceConnectionConfig {

    @ConstructorBinding
    public ConfluenceConfig {

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(
                "Invalid Confluence configuration: 'confluence.base-url' (CONFLUENCE_BASE_URL) is required. " +
                "Check your environment variables or Infisical secrets."
            );
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                "Invalid Confluence configuration: 'confluence.username' (CONFLUENCE_USERNAME) is required. " +
                "Check your environment variables or Infisical secrets."
            );
        }
        if (apiToken == null || apiToken.isBlank()) {
            throw new IllegalArgumentException(
                "Invalid Confluence configuration: 'confluence.api-token' (CONFLUENCE_API_TOKEN) is required. " +
                "Check your environment variables or Infisical secrets."
            );
        }

        log.info("Confluence configuration initialized");
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
            && apiToken != null && !apiToken.isBlank();
    }

    @Override
    public String getRestApiUrl() {
        return baseUrl.endsWith("/")
            ? baseUrl + "rest/api"
            : baseUrl + "/rest/api";
    }

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

    public record ApiPaths(
        String contentPath,
        String searchContentPath,
        String spacePath,
        String attachmentChildSuffix,
        String defaultPageExpands,
        String defaultSpaceExpands
    ) {}

    public record ProxySettings(
        String host,
        int port,
        String username,
        String password
    ) {}

    public record CacheSettings(
        long refreshIntervalMs,
        long initialDelayMs
    ) {
        public CacheSettings {
            if (refreshIntervalMs <= 0) {
                refreshIntervalMs = 300000; // 5 minutes default
            }
            if (initialDelayMs < 0) {
                initialDelayMs = 5000; // 5 seconds default
            }
        }
    }

    public record PollingSettings(
        long intervalMs
    ) {
        public PollingSettings {
            if (intervalMs <= 0) {
                intervalMs = 60000; // 1 minute default
            }
        }
    }
}
