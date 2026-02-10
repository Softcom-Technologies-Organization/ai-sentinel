package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.mapper.ConfluenceUrlBuilder;

/**
 * Initializes the global Confluence UI root URL for static URL building.
 * <p>
 * Business: Presentation URLs for Confluence must be built using the configured root URL.
 * This component wires the configuration into the static ConfluenceUrlBuilder at application start,
 * and refreshes it when configuration is updated via the UI.
 */
@Component
@Slf4j
public class ConfluenceUrlInitializer {

    private final ConfluenceConnectionConfig config;

    public ConfluenceUrlInitializer(@Qualifier("confluenceConfig") ConfluenceConnectionConfig config) {
        this.config = config;
        ConfluenceUrlBuilder.setGlobalRootUrl(config.baseUrl());
    }

    @EventListener
    public void onConfigUpdated(ConfluenceConfigUpdatedEvent event) {
        String newBaseUrl = config.baseUrl();
        log.info("Confluence config updated, refreshing URL builder with baseUrl: {}", newBaseUrl);
        ConfluenceUrlBuilder.setGlobalRootUrl(newBaseUrl);
    }
}
