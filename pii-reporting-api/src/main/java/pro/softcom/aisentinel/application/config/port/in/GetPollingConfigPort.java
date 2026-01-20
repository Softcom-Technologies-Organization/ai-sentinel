package pro.softcom.aisentinel.application.config.port.in;

import pro.softcom.aisentinel.domain.config.PollingConfig;

/**
 * In-port for retrieving polling configuration.
 * Used by inbound adapters (e.g., REST controllers) to get polling settings.
 */
public interface GetPollingConfigPort {

    /**
     * Retrieves the current polling configuration for frontend-backend coordination.
     *
     * @return the polling configuration
     */
    PollingConfig getPollingConfig();
}
