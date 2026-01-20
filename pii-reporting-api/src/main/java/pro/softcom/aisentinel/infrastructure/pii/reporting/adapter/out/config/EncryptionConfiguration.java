package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables the EncryptionConfig properties to be loaded from application.yml.
 */
@Configuration
@EnableConfigurationProperties(EncryptionConfig.class)
public class EncryptionConfiguration {
    // This class serves only to enable configuration properties
}
