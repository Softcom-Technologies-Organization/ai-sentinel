package pro.softcom.aisentinel.infrastructure.config.properties;

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
     * Controls access to the PII reveal endpoint (/reveal).
     * 
     * <p>Security model (defense in depth):</p>
     * <ul>
     *   <li><b>SSE streams:</b> Sensitive values are ALWAYS masked (null), regardless of this setting.
     *       This prevents accidental leaks via logs, network monitoring, or browser dev tools.</li>
     *   <li><b>/reveal endpoint:</b> When this property is true, authenticated users can explicitly
     *       request decrypted PII values. When false, the endpoint returns 403 Forbidden.</li>
     * </ul>
     * 
     * <p>Default: false (security by default - revealing PII requires explicit opt-in)</p>
     * 
     * <p>Business rule: Revealing PII values should be an intentional, auditable action,
     * never an automatic side effect of scan operations.</p>
     */
    private boolean allowSecretReveal = false;
}
