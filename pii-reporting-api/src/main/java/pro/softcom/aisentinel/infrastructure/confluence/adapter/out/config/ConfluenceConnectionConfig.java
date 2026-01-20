package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config;

/**
 * Vendor-agnostic contract for Confluence connection and API settings.
 * Exposes scalar values to avoid coupling with vendor-specific config records.
 */
public interface ConfluenceConnectionConfig {
    // Core connection
    String baseUrl();
    String username();
    String apiToken();

    // Timeouts, retries and proxy
    int connectTimeout();
    int readTimeout();
    int maxRetries();
    boolean enableProxy();
    String proxyHost();
    int proxyPort();
    String proxyUsername();
    String proxyPassword();

    // Pagination
    int pagesLimit();
    int maxPages();

    // API paths
    String contentPath();
    String searchContentPath();
    String spacePath();
    String attachmentChildSuffix();
    String defaultPageExpands();
    String defaultSpaceExpands();

    // Convenience
    default boolean isValid() {
        return notBlank(baseUrl()) && notBlank(username()) && notBlank(apiToken());
    }

    default String getRestApiUrl() {
        var base = baseUrl();
        return base.endsWith("/") ? base + "rest/api" : base + "/rest/api";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
