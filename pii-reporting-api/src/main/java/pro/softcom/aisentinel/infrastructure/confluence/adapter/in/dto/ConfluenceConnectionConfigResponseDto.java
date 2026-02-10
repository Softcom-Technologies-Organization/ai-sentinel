package pro.softcom.aisentinel.infrastructure.confluence.adapter.in.dto;

import java.time.LocalDateTime;

/**
 * DTO representing Confluence connection configuration for REST API responses.
 *
 * <p>Business purpose: Provides clients with current Confluence connection settings.
 * The API token is always masked for security.
 *
 * @param baseUrl        Base URL of the Confluence instance
 * @param username       Username for Confluence authentication
 * @param apiToken       Always masked as "***" for security
 * @param connectTimeout Connection timeout in milliseconds
 * @param readTimeout    Read timeout in milliseconds
 * @param maxRetries     Maximum number of retry attempts
 * @param pagesLimit     Number of pages per pagination request
 * @param maxPages       Maximum total pages to retrieve
 * @param updatedAt      Timestamp of last configuration update
 * @param updatedBy      User who last updated the configuration
 */
public record ConfluenceConnectionConfigResponseDto(
        String baseUrl,
        String username,
        String apiToken,
        int connectTimeout,
        int readTimeout,
        int maxRetries,
        int pagesLimit,
        int maxPages,
        LocalDateTime updatedAt,
        String updatedBy
) {
}
