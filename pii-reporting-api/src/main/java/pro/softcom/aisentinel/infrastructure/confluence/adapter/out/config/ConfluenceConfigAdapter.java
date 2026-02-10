package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.config.port.out.ReadConfluenceConfigPort;

/**
 * Adapter for reading Confluence cache/polling configuration from application properties.
 * Implements the out-port for hexagonal architecture compliance.
 */
@Component
public class ConfluenceConfigAdapter implements ReadConfluenceConfigPort {

    private final long cacheRefreshIntervalMs;
    private final long pollingIntervalMs;

    public ConfluenceConfigAdapter(
            @Value("${ai-sentinel.confluence.cache.refresh-interval-ms:300000}") long cacheRefreshIntervalMs,
            @Value("${ai-sentinel.confluence.polling.interval-ms:60000}") long pollingIntervalMs) {
        this.cacheRefreshIntervalMs = cacheRefreshIntervalMs;
        this.pollingIntervalMs = pollingIntervalMs;
    }

    @Override
    public long getCacheRefreshIntervalMs() {
        return cacheRefreshIntervalMs;
    }

    @Override
    public long getPollingIntervalMs() {
        return pollingIntervalMs;
    }
}
