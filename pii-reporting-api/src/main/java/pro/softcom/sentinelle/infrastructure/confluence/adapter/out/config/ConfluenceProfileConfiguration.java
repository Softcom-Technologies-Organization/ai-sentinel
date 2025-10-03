package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for Confluence.
 * Provides the Confluence configuration bean independent of any specific instance.
 * 
 * Environment variable values determine the targeted Confluence instance,
 * no reference to the Confluence origin remains in the code.
 */
@Configuration
public class ConfluenceProfileConfiguration {

    /**
     * Confluence configuration bean.
     * Uses the default configuration based on "confluence.*" properties.
     * 
     * @param config The Confluence configuration automatically injected by Spring
     * @return The Confluence configuration
     */
    @Bean("confluenceConfig")
    public ConfluenceConnectionConfig confluenceConfig(ConfluenceConfig config) {
        return config;
    }
}
