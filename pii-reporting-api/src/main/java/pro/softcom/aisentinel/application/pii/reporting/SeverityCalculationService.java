package pro.softcom.aisentinel.application.pii.reporting;

import static java.util.Map.entry;
import static pro.softcom.aisentinel.domain.pii.reporting.PiiSeverity.HIGH;
import static pro.softcom.aisentinel.domain.pii.reporting.PiiSeverity.LOW;
import static pro.softcom.aisentinel.domain.pii.reporting.PiiSeverity.MEDIUM;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.domain.pii.reporting.PiiSeverity;
import pro.softcom.aisentinel.domain.pii.reporting.SeverityCounts;

/**
 * Application service responsible for calculating PII severity levels and aggregating counts.
 * 
 * <p>This service implements the business rules for classifying PII types into severity levels
 * (HIGH, MEDIUM, LOW) based on their sensitivity and potential impact if exposed.
 * 
 * <h3>Classification Rules:</h3>
 * <ul>
 *   <li><b>HIGH (14 types)</b>: Financial credentials, authentication tokens, government IDs with high sensitivity
 *       <br>Examples: Credit cards, passwords, social security numbers</li>
 *   <li><b>MEDIUM (28 types)</b>: Official documents, identification numbers, personal details
 *       <br>Examples: Driver licenses, passports, dates of birth</li>
 *   <li><b>LOW (13 types)</b>: Contact information, location data, names
 *       <br>Examples: Email addresses, phone numbers, names</li>
 * </ul>
 * 
 * <p><b>Note:</b> Unknown PII types default to LOW severity as a safe fallback.
 * 
 * @see PiiSeverity
 * @see SeverityCounts
 */
@Slf4j
public class SeverityCalculationService {

    /**
     * Static mapping of PII type names to their severity levels.
     * This map defines the business rules for severity classification.
     */
    private static final Map<String, PiiSeverity> SEVERITY_RULES = Map.<String, PiiSeverity>ofEntries(
            // HIGH SEVERITY - Financial, authentication, highly sensitive government IDs
            entry("PASSWORD", HIGH),
            entry("API_KEY", HIGH),
            entry("GITHUB_TOKEN", HIGH),
            entry("AWS_ACCESS_KEY", HIGH),
            entry("JWT_TOKEN", HIGH),
            // Credit cards (ML detector and Presidio)
            entry("CREDITCARDNUMBER", HIGH),
            entry("CREDIT_CARD", HIGH),
            // Bank accounts (ML detector and Presidio)
            entry("ACCOUNTNUM", HIGH),
            entry("BANK_ACCOUNT", HIGH),
            entry("IBAN", HIGH),
            entry("CRYPTO_WALLET", HIGH),
            entry("US_BANK_NUMBER", HIGH),
            // Social Security Numbers (ML detector and Presidio)
            entry("SOCIALNUM", HIGH),
            entry("SSN", HIGH),
            entry("US_SSN", HIGH),
            entry("MEDICAL_LICENSE", HIGH),
            entry("AU_MEDICARE", HIGH),
            entry("IN_AADHAAR", HIGH),

            // MEDIUM SEVERITY (28 types) - Official documents, IDs, personal details
            entry("DRIVERLICENSENUM", MEDIUM),
            entry("IDCARDNUM", MEDIUM),
            entry("ID_CARD", MEDIUM),
            entry("TAXNUM", MEDIUM),
            entry("US_PASSPORT", MEDIUM),
            entry("US_DRIVER_LICENSE", MEDIUM),
            entry("US_ITIN", MEDIUM),
            entry("ES_NIF", MEDIUM),
            entry("ES_NIE", MEDIUM),
            entry("IT_FISCAL_CODE", MEDIUM),
            entry("IT_PASSPORT", MEDIUM),
            entry("IT_IDENTITY_CARD", MEDIUM),
            entry("IT_DRIVER_LICENSE", MEDIUM),
            entry("IT_VAT_CODE", MEDIUM),
            entry("PL_PESEL", MEDIUM),
            entry("SG_NRIC_FIN", MEDIUM),
            entry("SG_UEN", MEDIUM),
            entry("AU_TFN", MEDIUM),
            entry("AU_ABN", MEDIUM),
            entry("AU_ACN", MEDIUM),
            entry("IN_PAN", MEDIUM),
            entry("IN_VEHICLE_REGISTRATION", MEDIUM),
            entry("IN_VOTER", MEDIUM),
            entry("IN_PASSPORT", MEDIUM),
            entry("FI_PERSONAL_IDENTITY_CODE", MEDIUM),
            entry("KR_RRN", MEDIUM),
            entry("TH_TNIN", MEDIUM),
            entry("DATEOFBIRTH", MEDIUM),
            entry("AGE", MEDIUM),

            // LOW SEVERITY - Contact info, location, names
            entry("EMAIL", LOW),
            entry("TELEPHONENUM", LOW),
            entry("PHONE", LOW),
            entry("PHONE_NUMBER", LOW),
            entry("NAME", LOW),
            entry("GIVENNAME", LOW),
            entry("SURNAME", LOW),
            entry("USERNAME", LOW),
            entry("IP_ADDRESS", LOW),
            entry("MAC_ADDRESS", LOW),
            entry("CITY", LOW),
            entry("STREET", LOW),
            entry("BUILDINGNUM", LOW),
            entry("ZIPCODE", LOW)
    );

    /**
     * Calculates the severity level for a given PII type.
     * 
     * <p>The calculation is based on predefined business rules that map PII type names
     * to their corresponding severity levels. The lookup is case-insensitive and handles
     * leading/trailing whitespace.
     * 
     * <p><b>Default Behavior:</b> Unknown PII types default to {@link PiiSeverity#LOW} as a
     * safe fallback to ensure all detected PIIs are counted.
     * 
     * @param piiType The PII type name to classify (case-insensitive, whitespace-trimmed)
     * @return The severity level (HIGH, MEDIUM, or LOW). Never null.
     */
    public PiiSeverity calculateSeverity(String piiType) {
        String normalizedType = normalizeType(piiType);
        log.info("Calculating severity for PII type '{}' normalized to '{}'", piiType, normalizedType);

        PiiSeverity severity = SEVERITY_RULES.get(normalizedType);
        if (severity != null) {
            log.info("Found severity mapping for normalized PII type '{}': {}", normalizedType, severity);
            return severity;
        }

        log.info("No severity mapping found for normalized PII type '{}', using default severity: {}", normalizedType, LOW);
        return LOW;
    }

    /**
     * Aggregates severity counts from a list of PII entities.
     * 
     * <p>This method processes each entity, determines its severity level, and increments
     * the appropriate counter. It's designed to work with any entity type that has a
     * {@code piiType} accessor method.
     * 
     * <p><b>Usage Example:</b>
     * <pre>{@code
     * List<DetectedPii> piis = scanResults.getDetections();
     * SeverityCounts counts = service.aggregateCounts(piis);
     * // counts.high() -> number of HIGH severity PIIs
     * // counts.medium() -> number of MEDIUM severity PIIs
     * // counts.low() -> number of LOW severity PIIs
     * }</pre>
     * 
     * @param entities List of entities with PII type information
     * @param <T> Entity type that implements a {@code piiType()} method (typically a record)
     * @return Aggregated severity counts. Returns {@link SeverityCounts#zero()} for empty list.
     */
    public <T> SeverityCounts aggregateCounts(List<T> entities) {
        int highCount = 0;
        int mediumCount = 0;
        int lowCount = 0;

        for (T entity : entities) {
            // Use reflection-free approach - assumes entity has piiType() method
            // This works with records and any class with a piiType() getter
            String piiType = extractPiiType(entity);
            PiiSeverity severity = calculateSeverity(piiType);

            switch (severity) {
                case HIGH -> highCount++;
                case MEDIUM -> mediumCount++;
                case LOW -> lowCount++;
            }
        }

        return new SeverityCounts(highCount, mediumCount, lowCount);
    }

    /**
     * Normalizes a PII type name for lookup in the severity rules map.
     * 
     * <p>Normalization ensures consistent matching regardless of:
     * <ul>
     *   <li>Case variations (password, Password, PASSWORD)</li>
     *   <li>Leading/trailing whitespace</li>
     *   <li>Null values (returns empty string)</li>
     * </ul>
     * 
     * @param piiType The raw PII type name to normalize
     * @return Normalized type name (uppercase, trimmed). Empty string if input is null.
     */
    private String normalizeType(String piiType) {
        if (piiType == null) {
            return "";
        }
        return piiType.trim().toUpperCase();
    }

    /**
     * Extracts the PII type from an entity using its piiType() method.
     * 
     * <p>This method uses Java reflection to call the piiType() method on any entity type.
     * It's designed to work with records and POJOs that follow the convention of having
     * a {@code piiType()} accessor method.
     * 
     * @param entity The entity to extract the PII type from
     * @param <T> Entity type
     * @return The PII type string, or empty string if extraction fails
     */
    @SuppressWarnings("unchecked")
    private <T> String extractPiiType(T entity) {
        try {
            // Use reflection to call piiType() method
            var method = entity.getClass().getMethod("piiType");
            Object result = method.invoke(entity);
            return result != null ? result.toString() : "";
        } catch (Exception _) {
            // Fallback: try to use toString() or return empty string
            return "";
        }
    }
}
