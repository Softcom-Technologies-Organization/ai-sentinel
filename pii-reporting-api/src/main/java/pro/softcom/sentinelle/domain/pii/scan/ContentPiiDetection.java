package pro.softcom.sentinelle.domain.pii.scan;

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
    public enum DataType {
        EMAIL("Email", "Adresse email détectée"),
        PHONE("Téléphone", PHONE_NUMBER_LABEL),
        PHONE_NUMBER(PHONE_NUMBER_LABEL, "Numéro de téléphone formaté"),
        TELEPHONENUM(PHONE_NUMBER_LABEL, "Numéro de téléphone détecté"),
        PERSON("Personne", "Nom de personne détecté"),
        NAME("Nom", "Nom propre détecté"),
        SURNAME("Nom de famille", "Nom de famille détecté"),
        LOCATION("Localisation", "Lieu géographique"),
        ADDRESS("Adresse", "Adresse postale"),
        STREET("Rue", "Nom de rue détecté"),
        CITY("Ville", "Nom de ville détecté"),
        ZIPCODE("Code postal", "Code postal détecté"),
        BUILDINGNUM("Numéro de bâtiment", "Numéro de bâtiment détecté"),
        CREDIT_CARD("Carte de crédit", "Numéro de carte de crédit"),
        BANK_ACCOUNT("Compte bancaire", "Numéro de compte bancaire"),
        SSN("Numéro AVS", "Numéro de sécurité sociale"),
        ID_CARD("Carte d'identité", "Numéro de carte d'identité"),
        PASSWORD("Mot de passe", "Mot de passe détecté"),
        API_KEY("Clé API", "Clé d'API détectée"),
        TOKEN("Jeton", "Jeton d'authentification"),
        URL("URL", "Lien web détecté"),
        IP_ADDRESS("Adresse IP", "Adresse IP détectée"),
        UNKNOWN("Inconnu", "Type de données non identifié"),
        // Legacy values for backward compatibility
        AVS("AVS", "Numéro AVS (legacy)"),
        SECURITY("Sécurité", "Information de sécurité (legacy)"),
        ATTACHMENT("Pièce jointe", "Lien vers une pièce jointe (legacy)"), USERNAME("Nom d'utilisateur", "Nom utilisé" +
                " par un utilisateur pour s'identifier");
        
        private final String label;
        private final String description;
        
        DataType(String label, String description) {
            this.label = label;
            this.description = description;
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
        DataType type,
        String value,
        String context,
        int position,
        int end,
        Double score,
        String selector
    ) {
    }
}
