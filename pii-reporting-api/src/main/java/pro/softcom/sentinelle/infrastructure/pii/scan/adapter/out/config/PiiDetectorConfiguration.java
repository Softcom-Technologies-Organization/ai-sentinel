package pro.softcom.sentinelle.infrastructure.pii.scan.adapter.out.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for PII detector service.
 * Enables the PiiDetectorConfig properties to be loaded from application.yml.
 */
@Configuration
@EnableConfigurationProperties(PiiDetectorConfig.class)
public class PiiDetectorConfiguration {
    // This class serves only to enable configuration properties
}
