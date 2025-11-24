package pro.softcom.aisentinel.application.config.port.out;

/**
 * Out-port for reading Confluence configuration settings.
 * Abstracts the configuration source (properties, environment, etc.).
 */
public interface ReadConfluenceConfigPort {

    /**
     * Gets the backend cache refresh interval in milliseconds.
     *
     * @return refresh interval in milliseconds
     */
    long getCacheRefreshIntervalMs();

    /**
     * Gets the frontend polling interval recommendation in milliseconds.
     *
     * @return polling interval in milliseconds
     */
    long getPollingIntervalMs();
}
