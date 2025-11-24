package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.config.port.out.ReadConfluenceConfigPort;

/**
 * Adapter for reading Confluence configuration.
 * Implements the out-port for hexagonal architecture compliance.
 */
@Component
@RequiredArgsConstructor
public class ConfluenceConfigAdapter implements ReadConfluenceConfigPort {

    private final ConfluenceConfig confluenceConfig;

    @Override
    public long getCacheRefreshIntervalMs() {
        return confluenceConfig.cache().refreshIntervalMs();
    }

    @Override
    public long getPollingIntervalMs() {
        return confluenceConfig.polling().intervalMs();
    }
}
