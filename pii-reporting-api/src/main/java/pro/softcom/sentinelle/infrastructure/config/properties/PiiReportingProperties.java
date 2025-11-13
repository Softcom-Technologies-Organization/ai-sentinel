package pro.softcom.sentinelle.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for PII reporting features.
 * Business intent: control access to decrypted PII data via centralized configuration.
 */
@Configuration
@ConfigurationProperties(prefix = "pii.reporting")
@Data
public class PiiReportingProperties {
    
    /**
     * Allow revealing decrypted PII values to users.
     * When false, sensitiveValue is never sent to frontend via SSE
     * and the reveal endpoint returns 403 Forbidden.
     * Default: true
     */
    private boolean allowSecretReveal = true;
}
