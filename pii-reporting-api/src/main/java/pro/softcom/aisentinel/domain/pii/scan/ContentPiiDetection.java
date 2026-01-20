package pro.softcom.aisentinel.domain.pii.scan;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
     * These are business-level categories that group related PII types.
     */
    @Getter
    public enum PersonallyIdentifiableInformationType {
        // Contact Information
        EMAIL("Email"),
        PHONE("Téléphone"),
        PHONE_NUMBER(PHONE_NUMBER_LABEL),
        TELEPHONENUM(PHONE_NUMBER_LABEL),
        FAX("Fax"),

        // Identity
        DATE_OF_BIRTH("Date de naissance"),
        PERSON("Personne"),
        NAME("Nom"),
        SURNAME("Nom de famille"),
        USERNAME("Nom d'utilisateur"),
        GENDER("Genre"),
        NATIONALITY("Nationalité"),
        MARITAL_STATUS("État civil"),
        NRP("Nationalité, Religion ou Groupe Politique"),

        // Location
        LOCATION("Localisation"),
        ADDRESS("Adresse"),
        STREET("Rue"),
        CITY("Ville"),
        ZIPCODE("Code postal"),
        BUILDINGNUM("Numéro de bâtiment"),
        COUNTRY("Pays"),
        STATE("État/Province"),
        GPS("Coordonnées GPS"),

        // Financial
        CREDIT_CARD("Carte de crédit"),
        BANK_ACCOUNT("Compte bancaire"),
        IBAN("IBAN"),
        TAX("Numéro fiscal"),
        INVOICE("Facture"),
        SALARY("Salaire"),
        TRANSACTION("Transaction"),

        // Government IDs
        SSN("Numéro AVS"),
        ID_CARD("Carte d'identité"),
        PASSPORT("Passeport"),
        DRIVER_LICENSE("Permis de conduire"),

        // IT & Credentials
        PASSWORD("Mot de passe"),
        API_KEY("Clé API"),
        TOKEN("Jeton"),
        SECRET("Secret"),
        URL("URL"),
        IP_ADDRESS("Adresse IP"),
        MAC_ADDRESS("Adresse MAC"),
        HOSTNAME("Nom d'hôte"),
        DEVICE("Appareil"),
        SESSION("Session"),

        // Medical/Healthcare
        MEDICAL("Information médicale"),
        PATIENT("Patient"),
        DIAGNOSIS("Diagnostic"),
        MEDICATION("Médicament"),
        HEALTH_INSURANCE("Assurance maladie"),
        DOCTOR("Médecin"),
        HOSPITAL("Hôpital"),

        // Professional
        EMPLOYEE("Employé"),
        JOB_TITLE("Poste"),
        COMPANY("Entreprise"),
        DEPARTMENT("Département"),
        STUDENT("Étudiant"),
        SCHOOL("École"),

        // Legal
        LEGAL("Information légale"),
        CASE_NUMBER("Numéro de dossier"),
        COURT("Tribunal"),
        CRIMINAL_RECORD("Casier judiciaire"),
        LICENSE("Licence"),
        PERMIT("Permis"),
        IMMIGRATION("Immigration"),

        // Assets
        VEHICLE("Véhicule"),
        LICENSE_PLATE("Plaque d'immatriculation"),
        VIN("Numéro VIN"),
        PROPERTY("Propriété"),
        INSURANCE("Assurance"),

        // Biometric
        BIOMETRIC("Données biométriques"),
        FINGERPRINT("Empreinte digitale"),
        FACIAL("Reconnaissance faciale"),
        IRIS("Scan iris"),
        VOICE("Empreinte vocale"),
        DNA("ADN"),

        // Temporal
        DATE("Date"),
        TIME("Heure"),
        TIMESTAMP("Horodatage"),
        AGE("Âge"),

        // Digital Identity
        SOCIAL_MEDIA("Réseaux sociaux"),
        ONLINE_HANDLE("Pseudonyme"),
        ACCOUNT("Compte"),
        CUSTOMER("Client"),

        // Other
        UNKNOWN("Inconnu"),
        // Legacy values for backward compatibility
        AVS("AVS"),
        SECURITY("Sécurité"),
        ATTACHMENT("Pièce jointe");

        private final String label;

        PersonallyIdentifiableInformationType(String label) {
            this.label = label;
        }

    }

    /**
     * Source of the detected PII entity.
     */
    public enum DetectorSource {
        UNKNOWN_SOURCE,
        GLINER,
        PRESIDIO,
        REGEX
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
     * @param source source of the detected PII entity (e.g. GLiNER, Presidio)
     */
    public record SensitiveData(
        PersonallyIdentifiableInformationType type,
        String value,
        String context,
        int position,
        int end,
        Double score,
        String selector,
        DetectorSource source
    ) {
    }
}