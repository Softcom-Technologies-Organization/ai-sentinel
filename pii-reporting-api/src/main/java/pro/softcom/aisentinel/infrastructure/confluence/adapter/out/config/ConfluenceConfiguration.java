package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for Confluence infrastructure.
 * Enables configuration properties for Confluence connection.
 * 
 * This class serves to explicitly register the configuration properties class
 * used by the Confluence module.
 * The configuration is independent of the targeted Confluence instance.
 */
@Configuration
@EnableConfigurationProperties(ConfluenceConfig.class)
public class ConfluenceConfiguration {
    // This class only serves to enable configuration properties
    // Beans are automatically created by Spring Boot
}
