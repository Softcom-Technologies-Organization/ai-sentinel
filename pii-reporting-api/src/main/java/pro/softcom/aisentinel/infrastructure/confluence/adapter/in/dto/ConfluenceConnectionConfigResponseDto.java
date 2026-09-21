package pro.softcom.aisentinel.infrastructure.confluence.adapter.in.dto;

import java.time.Instant;

/**
 * DTO representing Confluence connection configuration for REST API responses.
 *
 * <p>Business purpose: Provides clients with current Confluence connection settings.
 *
 * @param baseUrl        Base URL of the Confluence instance
 * @param username       Username for Confluence authentication
 * @param connectTimeout Connection timeout in milliseconds
 * @param readTimeout    Read timeout in milliseconds
 * @param maxRetries     Maximum number of retry attempts
 * @param pagesLimit     Number of pages per pagination request
 * @param deploymentType Type of Confluence deployment ("CLOUD" or "DATA_CENTER")
 * @param updatedAt      Timestamp of last configuration update
 * @param updatedBy      User who last updated the configuration
 * @param configured     Whether real credentials have been saved (non-placeholder values)
 */
public record ConfluenceConnectionConfigResponseDto(
        String baseUrl,
        String username,
        int connectTimeout,
        int readTimeout,
        int maxRetries,
        int pagesLimit,
        String deploymentType,
        Instant updatedAt,
        String updatedBy,
        boolean configured
) {
}
