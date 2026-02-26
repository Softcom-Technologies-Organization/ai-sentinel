package pro.softcom.aisentinel.application.confluence.port.in;

import pro.softcom.aisentinel.domain.confluence.ConfluenceConnectionSettings;

import java.util.Objects;

/**
 * Port IN for managing Confluence connection configuration.
 * Defines use cases for retrieving, updating, and testing Confluence connection settings.
 */
public interface ManageConfluenceConnectionPort {

    /**
     * Retrieves the current Confluence connection settings.
     *
     * @return The current connection settings
     */
    ConfluenceConnectionSettings getConnectionSettings();

    /**
     * Checks whether real Confluence credentials have been configured.
     *
     * @return true if a non-empty encrypted API token exists in the database
     */
    boolean isConfigured();

    /**
     * Updates the Confluence connection configuration.
     *
     * @param command The update command containing new configuration values
     * @return The updated connection settings
     * @throws IllegalArgumentException if command validation fails
     */
    ConfluenceConnectionSettings updateConnectionSettings(UpdateConfluenceConnectionCommand command);

    /**
     * Tests the Confluence connection using the provided settings.
     *
     * @param command The test command containing connection parameters
     * @return true if the connection is successful, false otherwise
     */
    boolean testConnection(TestConfluenceConnectionCommand command);

    /**
     * Command to update Confluence connection configuration.
     *
     * @param baseUrl        Base URL of the Confluence instance
     * @param username       Username for Confluence authentication
     * @param apiToken       Plain-text API token (will be encrypted before storage)
     * @param connectTimeout Connection timeout in milliseconds
     * @param readTimeout    Read timeout in milliseconds
     * @param maxRetries     Maximum number of retry attempts
     * @param pagesLimit     Number of pages per pagination request
     * @param maxPages       Maximum total pages to retrieve
     * @param updatedBy      User identifier who is updating the configuration
     */
    record UpdateConfluenceConnectionCommand(
            String baseUrl,
            String username,
            String apiToken,
            int connectTimeout,
            int readTimeout,
            int maxRetries,
            int pagesLimit,
            int maxPages,
            String updatedBy
    ) {
        public UpdateConfluenceConnectionCommand {
            Objects.requireNonNull(baseUrl, "baseUrl must not be null");
            Objects.requireNonNull(username, "username must not be null");
            Objects.requireNonNull(updatedBy, "updatedBy must not be null");
        }
    }

    /**
     * Command to test Confluence connection.
     *
     * @param baseUrl  Base URL of the Confluence instance
     * @param username Username for Confluence authentication
     * @param apiToken Plain-text API token for testing
     */
    record TestConfluenceConnectionCommand(
            String baseUrl,
            String username,
            String apiToken
    ) {
        public TestConfluenceConnectionCommand {
            Objects.requireNonNull(baseUrl, "baseUrl must not be null");
            Objects.requireNonNull(username, "username must not be null");
        }
    }
}
