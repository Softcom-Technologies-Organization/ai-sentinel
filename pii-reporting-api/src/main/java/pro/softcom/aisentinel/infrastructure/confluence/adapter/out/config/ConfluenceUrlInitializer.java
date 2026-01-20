package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.mapper.ConfluenceUrlBuilder;

/**
 * Initializes the global Confluence UI root URL for static URL building.
 * <p>
 * Business: Presentation URLs for Confluence must be built using the configured root URL.
 * This component wires the configuration into the static ConfluenceUrlBuilder at application start.
 */
@Component
public class ConfluenceUrlInitializer {

    /**
     * Injects the configured baseUrl into the global URL builder, agnostic of vendor.
     * @param config configuration holding the Confluence base URL
     */
    public ConfluenceUrlInitializer(@Qualifier("confluenceConfig") ConfluenceConnectionConfig config) {
        ConfluenceUrlBuilder.setGlobalRootUrl(config.baseUrl());
    }
}
