package pro.softcom.aisentinel.domain.confluence;

import java.time.LocalDateTime;

/**
 * Domain model for Confluence connection configuration.
 * Represents the connection settings for the Confluence integration.
 * This is the single source of truth for connection configuration in the system.
 *
 * <p>Note: The API token is NOT part of the domain model because it is an infrastructure
 * concern (encrypted storage). The domain only deals with non-sensitive connection parameters.
 *
 * @param id             Configuration identifier (always 1, singleton)
 * @param baseUrl        Base URL of the Confluence instance
 * @param username       Username for Confluence authentication
 * @param connectTimeout Connection timeout in milliseconds
 * @param readTimeout    Read timeout in milliseconds
 * @param maxRetries     Maximum number of retry attempts
 * @param pagesLimit     Number of pages per pagination request
 * @param maxPages       Maximum total pages to retrieve
 * @param updatedAt      Timestamp of last configuration update
 * @param updatedBy      User who last updated the configuration
 */
public record ConfluenceConnectionSettings(
        Integer id,
        String baseUrl,
        String username,
        int connectTimeout,
        int readTimeout,
        int maxRetries,
        int pagesLimit,
        int maxPages,
        LocalDateTime updatedAt,
        String updatedBy) {

    /**
     * Compact constructor for validation.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public ConfluenceConnectionSettings {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Base URL cannot be null or blank");
        }

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }

        if (connectTimeout <= 0) {
            throw new IllegalArgumentException("Connect timeout must be positive");
        }

        if (readTimeout <= 0) {
            throw new IllegalArgumentException("Read timeout must be positive");
        }

        if (maxRetries < 0) {
            throw new IllegalArgumentException("Max retries cannot be negative");
        }

        if (pagesLimit <= 0) {
            throw new IllegalArgumentException("Pages limit must be positive");
        }

        if (maxPages <= 0) {
            throw new IllegalArgumentException("Max pages must be positive");
        }
    }
}
