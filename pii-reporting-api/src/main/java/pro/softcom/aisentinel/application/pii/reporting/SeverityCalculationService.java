package pro.softcom.aisentinel.application.pii.reporting;

import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;
import pro.softcom.aisentinel.domain.pii.reporting.SeverityCounts;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity.HIGH;
import static pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity.LOW;
import static pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity.MEDIUM;

/**
 * Application service responsible for calculating PII severity levels and aggregating counts.
 *
 * <p>This service implements the business rules for classifying PII types into severity levels
 * (HIGH, MEDIUM, LOW) based on their sensitivity and potential impact if exposed.
 *
 * <h3>Classification Rules (CONSOLIDATED - 44 types):</h3>
 * <ul>
 *   <li><b>HIGH (12 types)</b>: Financial credentials, authentication secrets, SSN
 *       <br>Examples: Credit cards, passwords, API keys, social security numbers</li>
 *   <li><b>MEDIUM (18 types)</b>: Official documents, medical info, legal records
 *       <br>Examples: Driver licenses, passports, diagnoses, case numbers</li>
 *   <li><b>LOW (14 types)</b>: Contact information, identifiers, names
 *       <br>Examples: Email addresses, phone numbers, usernames, IP addresses</li>
 * </ul>
 *
 * <p><b>Note:</b> Unknown PII types default to LOW severity as a safe fallback.
 * Legacy type names are supported for backward compatibility.
 *
 * @see PersonallyIdentifiableInformationSeverity
 * @see SeverityCounts
 */
@Slf4j
public class SeverityCalculationService {

    /**
     * Static mapping of PII type names to their severity levels.
     * CONSOLIDATED VERSION: 44 types across 7 categories + legacy mappings.
     */
    private static final Map<String, PersonallyIdentifiableInformationSeverity> SEVERITY_RULES = Map.<String, PersonallyIdentifiableInformationSeverity>ofEntries(
            // =========================================================================
            // HIGH SEVERITY - Financial, credentials, SSN (14 types)
            // =========================================================================
            // Financial
            entry("CREDIT_CARD_NUMBER", HIGH),
            entry("BANK_ACCOUNT_NUMBER", HIGH),
            entry("IBAN", HIGH),
            entry("BIC_SWIFT", HIGH),
            entry("CRYPTO_WALLET", HIGH),
            // IT Credentials (secrets)
            entry("PASSWORD", HIGH),
            entry("API_KEY", HIGH),
            entry("ACCESS_TOKEN", HIGH),
            entry("SECRET_KEY", HIGH),
            entry("SESSION_ID", HIGH),
            // Identity (high risk)
            entry("SSN", HIGH),
            entry("AVS_NUMBER", HIGH),
            // Medical (high risk)
            entry("MEDICAL_LICENSE", HIGH),
            // Legal (high risk)
            entry("CRIMINAL_RECORD", HIGH),

            // =========================================================================
            // MEDIUM SEVERITY - Documents, medical, identifiers (38 types)
            // =========================================================================
            // Identity
            entry("NATIONAL_ID", MEDIUM),
            entry("PASSPORT_NUMBER", MEDIUM),
            entry("DRIVER_LICENSE_NUMBER", MEDIUM),
            entry("DATE_OF_BIRTH", MEDIUM),
            entry("AGE", MEDIUM),
            entry("ES_NIF", MEDIUM),
            entry("ES_NIE", MEDIUM),
            entry("IT_PASSPORT", MEDIUM),
            entry("IT_IDENTITY_CARD", MEDIUM),
            entry("IT_DRIVER_LICENSE", MEDIUM),
            entry("PL_PESEL", MEDIUM),
            entry("SG_NRIC_FIN", MEDIUM),
            entry("IN_VOTER", MEDIUM),
            entry("IN_PASSPORT", MEDIUM),
            entry("FI_PERSONAL_IDENTITY_CODE", MEDIUM),
            entry("KR_RRN", MEDIUM),
            entry("TH_TNIN", MEDIUM),
            // Financial (lower risk)
            entry("TAX_ID", MEDIUM),
            entry("SALARY", MEDIUM),
            entry("US_ITIN", MEDIUM),
            entry("IT_FISCAL_CODE", MEDIUM),
            entry("IT_VAT_CODE", MEDIUM),
            entry("SG_UEN", MEDIUM),
            entry("AU_TFN", MEDIUM),
            entry("AU_ABN", MEDIUM),
            entry("AU_ACN", MEDIUM),
            entry("IN_PAN", MEDIUM),
            // Medical
            entry("PATIENT_ID", MEDIUM),
            entry("MEDICAL_RECORD_NUMBER", MEDIUM),
            entry("HEALTH_INSURANCE_NUMBER", MEDIUM),
            entry("DIAGNOSIS", MEDIUM),
            entry("MEDICATION", MEDIUM),
            // IT (device/network identifiers)
            entry("DEVICE_ID", MEDIUM),
            // Legal/Asset
            entry("CASE_NUMBER", MEDIUM),
            entry("LICENSE_NUMBER", MEDIUM),
            entry("VEHICLE_REGISTRATION", MEDIUM),
            entry("IN_VEHICLE_REGISTRATION", MEDIUM),
            entry("VIN", MEDIUM),
            entry("INSURANCE_POLICY_NUMBER", MEDIUM),

            // =========================================================================
            // LOW SEVERITY - Contact info, general identifiers (14 types)
            // =========================================================================
            // Identity
            entry("PERSON_NAME", LOW),
            entry("GENDER", LOW),
            entry("NATIONALITY", LOW),
            // Contact
            entry("EMAIL", LOW),
            entry("PHONE_NUMBER", LOW),
            entry("ADDRESS", LOW),
            entry("POSTAL_CODE", LOW),
            // Digital
            entry("USERNAME", LOW),
            entry("ACCOUNT_ID", LOW),
            entry("URL", LOW),
            // IT (network)
            entry("IP_ADDRESS", LOW),
            entry("MAC_ADDRESS", LOW),
            entry("HOSTNAME", LOW),
            // Legal/Asset
            entry("LICENSE_PLATE", LOW),
            // Temporal
            entry("TIMESTAMP", LOW),
            entry("DATE", LOW),
            entry("TIME", LOW),

            // =========================================================================
            // LEGACY MAPPINGS - Backward compatibility with old type names
            // =========================================================================
            // Credit cards legacy
            entry("CREDITCARDNUMBER", HIGH),
            entry("CREDIT_CARD", HIGH),
            entry("DEBIT_CARD_NUMBER", HIGH),
            // Bank accounts legacy
            entry("ACCOUNTNUM", HIGH),
            entry("BANK_ACCOUNT", HIGH),
            entry("BANKACCOUNT", HIGH),
            entry("US_BANK_NUMBER", HIGH),
            // SSN legacy
            entry("SOCIALNUM", HIGH),
            entry("US_SSN", HIGH),
            entry("AVSNUM", HIGH),
            // Tokens legacy
            entry("TOKEN", HIGH),
            entry("GITHUB_TOKEN", HIGH),
            entry("AWS_ACCESS_KEY", HIGH),
            entry("JWT_TOKEN", HIGH),
            // Identity legacy
            entry("DRIVERLICENSENUM", MEDIUM),
            entry("DRIVER_LICENSE", MEDIUM),
            entry("US_DRIVER_LICENSE", MEDIUM),
            entry("IDCARDNUM", MEDIUM),
            entry("ID_CARD", MEDIUM),
            entry("ID_CARD_NUMBER", MEDIUM),
            entry("PASSPORT", MEDIUM),
            entry("PASSPORTNUM", MEDIUM),
            entry("US_PASSPORT", MEDIUM),
            entry("TAXNUM", MEDIUM),
            entry("DATEOFBIRTH", MEDIUM),
            entry("BIRTH_DATE", MEDIUM),
            entry("DOB", MEDIUM),
            // Medical legacy
            entry("HEALTH_INSURANCE", HIGH),
            entry("MEDICAL_RECORD", HIGH),
            entry("AU_MEDICARE", HIGH),
            entry("IN_AADHAAR", HIGH),
            // Name legacy
            entry("NAME", LOW),
            entry("GIVENNAME", LOW),
            entry("SURNAME", LOW),
            entry("FIRST_NAME", LOW),
            entry("LAST_NAME", LOW),
            entry("FULL_NAME", LOW),
            // Phone legacy
            entry("TELEPHONENUM", LOW),
            entry("PHONE", LOW),
            entry("MOBILE_PHONE", LOW),
            // Address legacy
            entry("HOME_ADDRESS", LOW),
            entry("MAILING_ADDRESS", LOW),
            entry("STREET", LOW),
            entry("LOCATION", LOW),
            entry("CITY", LOW),
            entry("STATE", LOW),
            entry("COUNTRY", LOW),
            entry("ZIPCODE", LOW),
            entry("BUILDINGNUM", LOW),
            // IT legacy
            entry("IPADDRESS", LOW),
            entry("MACADDRESS", LOW)
    );

    /**
     * Calculates the severity level for a given PII type.
     * 
     * <p>The calculation is based on predefined business rules that map PII type names
     * to their corresponding severity levels. The lookup is case-insensitive and handles
     * leading/trailing whitespace.
     * 
     * <p><b>Default Behavior:</b> Unknown PII types default to {@link PersonallyIdentifiableInformationSeverity#LOW} as a
     * safe fallback to ensure all detected PIIs are counted.
     * 
     * @param piiType The PII type name to classify (case-insensitive, whitespace-trimmed)
     * @return The severity level (HIGH, MEDIUM, or LOW). Never null.
     */
    public PersonallyIdentifiableInformationSeverity calculateSeverity(String piiType) {
        String normalizedType = normalizeType(piiType);
        log.debug("Calculating severity for PII type '{}' normalized to '{}'", piiType, normalizedType);

        PersonallyIdentifiableInformationSeverity severity = SEVERITY_RULES.get(normalizedType);
        if (severity != null) {
            log.debug("Found severity mapping for normalized PII type '{}': {}", normalizedType, severity);
            return severity;
        }

        log.warn("No severity mapping found for normalized PII type '{}', using default severity: {}", normalizedType, LOW);
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
            PersonallyIdentifiableInformationSeverity severity = calculateSeverity(piiType);

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