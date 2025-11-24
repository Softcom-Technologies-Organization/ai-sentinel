package pro.softcom.aisentinel.infrastructure.document.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for document processing infrastructure.
 * Enables configuration properties for text quality assessment.
 * <p>
 * This class serves to explicitly register the configuration properties class
 * used by the text quality validation module.
 * The configuration applies to all document sources (Confluence, SharePoint, Google Drive, etc.).
 */
@Configuration
@EnableConfigurationProperties(TextQualityThresholds.class)
public class DocumentConfiguration {
    // This class only serves to enable configuration properties
    // Beans are automatically created by Spring Boot
}
