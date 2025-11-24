package pro.softcom.aisentinel.domain.pii.reporting;

/**
 * Represents the severity level of detected Personally Identifiable Information (PII).
 * 
 * <p>Severity is determined by the sensitivity and potential impact of the PII type.
 * The natural ordering of this enum reflects severity levels from most to least severe:
 * HIGH > MEDIUM > LOW.
 * 
 * <h3>Severity Levels:</h3>
 * <ul>
 *   <li><b>HIGH</b> - Highly sensitive information that poses significant risk if exposed
 *       (e.g., credit card numbers, social security numbers, passwords)</li>
 *   <li><b>MEDIUM</b> - Moderately sensitive information that requires protection
 *       (e.g., email addresses, phone numbers, dates of birth)</li>
 *   <li><b>LOW</b> - Less sensitive information that still qualifies as PII
 *       (e.g., names, organizations, locations)</li>
 * </ul>
 * 
 * <p>This enum is used throughout the domain layer to classify and aggregate PII detections
 * based on their severity.
 * 
 * @see SeverityCounts
 */
public enum PiiSeverity {
    /**
     * Highest severity level for highly sensitive PII.
     * Examples: Credit card numbers, passwords, social security numbers.
     */
    HIGH,
    
    /**
     * Medium severity level for moderately sensitive PII.
     * Examples: Email addresses, phone numbers, dates of birth.
     */
    MEDIUM,
    
    /**
     * Lowest severity level for less sensitive PII.
     * Examples: Names, organizations, locations.
     */
    LOW
}
