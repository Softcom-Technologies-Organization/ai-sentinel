package pro.softcom.aisentinel.application.confluence.port.out;

import pro.softcom.aisentinel.domain.confluence.ConfluenceConnectionSettings;

/**
 * Port OUT for Confluence connection configuration persistence.
 * Defines repository operations for Confluence connection settings.
 */
public interface ConfluenceConnectionConfigRepository {

    /**
     * Retrieves the current Confluence connection configuration.
     * Since configuration is a singleton (single row), this always returns the config.
     *
     * @return The current Confluence connection settings
     * @throws RuntimeException if configuration cannot be retrieved
     */
    ConfluenceConnectionSettings findConfig();

    /**
     * Updates the Confluence connection configuration.
     * Since configuration is a singleton (single row), this updates the existing config.
     *
     * @param settings       The new connection settings to persist
     * @param encryptedToken The encrypted API token to store
     * @throws IllegalArgumentException if settings are invalid
     * @throws RuntimeException         if update fails
     */
    void updateConfig(ConfluenceConnectionSettings settings, String encryptedToken);

    /**
     * Retrieves the encrypted API token from storage.
     *
     * @return The encrypted API token, or empty string if not set
     */
    String getEncryptedApiToken();
}
