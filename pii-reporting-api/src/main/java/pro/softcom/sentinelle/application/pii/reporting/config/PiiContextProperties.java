package pro.softcom.sentinelle.application.pii.reporting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for PII context extraction.
 * <p>
 * Controls how much context is extracted and displayed around detected PII.
 */
@Data
@Component
@ConfigurationProperties(prefix = "pii.context")
public class PiiContextProperties {
    
    /**
     * Maximum length of context to present to the user.
     * Beyond this length, the context is truncated to avoid overloading the interface.
     * Default: 200 characters
     */
    private int maxLength = 200;
    
    /**
     * Length on each side of the PII during truncation.
     * Allows keeping enough context before and after the masked PII.
     * Default: 80 characters
     */
    private int sideLength = 80;
}
