package pro.softcom.aisentinel.domain.pii.reporting;

import lombok.Builder;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorSource;

/**
 * Represents a detected PII with its metadata and sensitive values.
 * 
 * <p>Business rules:</p>
 * <ul>
 *   <li>sensitiveValue: the actual PII value detected (e.g., "john.doe@example.com")</li>
 *   <li>sensitiveContext: the surrounding text with the PII in clear</li>
 *   <li>maskedContext: the surrounding text with the PII masked (e.g., "Email: j***@e***.com")</li>
 * </ul>
 * 
 * <p>Security: Use {@link #withMaskedSensitiveData()} to create a safe version
 * for transmission to frontend via SSE, preventing accidental leaks.</p>
 */
@Builder(toBuilder = true)
public record DetectedPersonallyIdentifiableInformation(
        int startPosition,
        int endPosition,
        String piiType,
        String piiTypeLabel,
        double confidence,
        String sensitiveValue,
        String sensitiveContext,
        String maskedContext,
        DetectorSource source
) {
    /**
     * Creates a safe copy with sensitive data masked (set to null).
     * 
     * <p>This method implements the business rule: sensitive values must never
     * be transmitted via SSE streams to prevent accidental exposure through
     * logs, network monitoring, or browser developer tools.</p>
     * 
     * <p>Only the masked context is preserved, allowing the UI to display
     * the detection without revealing the actual sensitive value.</p>
     * 
     * @return a new instance with sensitiveValue and sensitiveContext set to null
     */
    public DetectedPersonallyIdentifiableInformation withMaskedSensitiveData() {
        return this.toBuilder()
                .sensitiveValue(null)
                .sensitiveContext(null)
                .build();
    }
}