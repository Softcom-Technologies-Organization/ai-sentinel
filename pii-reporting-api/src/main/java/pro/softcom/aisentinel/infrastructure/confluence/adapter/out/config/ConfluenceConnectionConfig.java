package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config;

import pro.softcom.aisentinel.domain.confluence.ConfluenceDeploymentType;

/**
 * Contract for user-configurable Confluence connection settings.
 * API paths and URL construction are handled by {@code ConfluenceApiUrlBuilder}.
 */
public interface ConfluenceConnectionConfig {

    String baseUrl();
    String username();
    String apiToken();

    int connectTimeout();
    int readTimeout();
    int maxRetries();

    int pagesLimit();

    ConfluenceDeploymentType deploymentType();

    default boolean isValid() {
        if (!notBlank(baseUrl()) || !notBlank(apiToken())) {
            return false;
        }
        return deploymentType() == ConfluenceDeploymentType.DATA_CENTER || notBlank(username());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
