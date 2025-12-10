package pro.softcom.aisentinel.domain.pii.scan;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * Represents PII findings for a single Confluence page.
 * Business purpose: captures what sensitive elements were detected on a page
 * along with basic statistics and the analysis timestamp. This is a pure
 * domain read model used by reporting and risk scoring.
 *
 * @param pageId unique identifier of the page
 * @param pageTitle human-readable title of the page
 * @param spaceKey business key of the Confluence space
 * @param analysisDate timestamp when the analysis was performed
 * @param sensitiveDataFound list of detected sensitive elements on the page
 * @param statistics aggregated counters for the page (keyed by metric name)
 */
@Builder
public record ContentPiiDetection(
    String pageId,
    String pageTitle,
    String spaceKey,
    LocalDateTime analysisDate,
    List<SensitiveData> sensitiveDataFound,
    Map<String, Integer> statistics
) {
    
    // Sonar S1192: avoid duplicate literal for phone number label
    /** Label used for all phone number related data types. */
    public static final String PHONE_NUMBER_LABEL = "Numéro de téléphone";

    /**
     * Detectable categories of sensitive data found during analysis.
     */
    @Getter
    public enum PersonallyIdentifiableInformationType {
        EMAIL("Email"),
        PHONE("Téléphone"),
        PHONE_NUMBER(PHONE_NUMBER_LABEL),
        TELEPHONENUM(PHONE_NUMBER_LABEL),
        DATE_OF_BIRTH("Date de naissance"),
        PERSON("Personne"),
        NAME("Nom"),
        SURNAME("Nom de famille"),
        LOCATION("Localisation"),
        ADDRESS("Adresse"),
        STREET("Rue"),
        CITY("Ville"),
        ZIPCODE("Code postal"),
        BUILDINGNUM("Numéro de bâtiment"),
        CREDIT_CARD("Carte de crédit"),
        BANK_ACCOUNT("Compte bancaire"),
        SSN("Numéro AVS"),
        ID_CARD("Carte d'identité"),
        PASSWORD("Mot de passe"),
        API_KEY("Clé API"),
        TOKEN("Jeton"),
        URL("URL"),
        IP_ADDRESS("Adresse IP"),
        UNKNOWN("Inconnu"),
        // Legacy values for backward compatibility
        AVS("AVS"),
        SECURITY("Sécurité"),
        ATTACHMENT("Pièce jointe"),
        USERNAME("Nom d'utilisateur");
        
        private final String label;

        PersonallyIdentifiableInformationType(String label) {
            this.label = label;
        }

    }
    
    /**
     * Represents a single sensitive element detected on the page.
     *
     * @param type business category of the detected element
     * @param value raw value as found in the content
     * @param context short surrounding text to help understand the occurrence
     * @param position start index of the occurrence in the content
     * @param end end index of the occurrence in the content
     * @param score confidence score provided by the detector (may be null)
     * @param selector optional selector or hint pointing to the element location
     */
    public record SensitiveData(
        PersonallyIdentifiableInformationType type,
        String value,
        String context,
        int position,
        int end,
        Double score,
        String selector
    ) {
    }
}
