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
 * @param discardedByPostfilter elements detected by the detectors but rejected
 *        by the precision post-filter (empty when the post-filter is disabled);
 *        exposed to measure the post-filter's false-positive reduction
 * @param detectorRunStats per-detector execution stats for this analysis (one
 *        entry per detector that actually ran, even at zero detection); empty
 *        when the detection service does not report them
 */
@Builder
public record ContentPiiDetection(
    String pageId,
    String pageTitle,
    String spaceKey,
    LocalDateTime analysisDate,
    List<SensitiveData> sensitiveDataFound,
    Map<String, Integer> statistics,
    List<DiscardedSensitiveData> discardedByPostfilter,
    List<DetectorRunStat> detectorRunStats
) {

    public ContentPiiDetection {
        // Builder callers predating the post-filter measurement never set the field.
        discardedByPostfilter = discardedByPostfilter == null ? List.of() : discardedByPostfilter;
        // Builder callers predating detector-stats collection never set the field.
        detectorRunStats = detectorRunStats == null ? List.of() : detectorRunStats;
    }
    
    // Sonar S1192: avoid duplicate literal for phone number label
    /** Label used for all phone number related data types. */
    public static final String PHONE_NUMBER_LABEL = "Numéro de téléphone";

    // Sonar S1192: avoid duplicate literal for postal code label (ZIPCODE/ZIP_CODE/POSTAL_CODE)
    /** Label used for all postal/zip code related data types. */
    public static final String POSTAL_CODE_LABEL = "Code postal";

    /**
     * Detectable categories of sensitive data found during analysis.
     * These are business-level categories that group related PII types.
     */
    @Getter
    public enum PersonallyIdentifiableInformationType {
        // Contact Information
        EMAIL("Email"),
        EMAIL_ADDRESS("Email"),
        PHONE("Téléphone"),
        PHONE_NUMBER(PHONE_NUMBER_LABEL),
        TELEPHONENUM(PHONE_NUMBER_LABEL),
        FAX("Fax"),
        FAX_NUMBER("Numéro de fax"),

        // Identity
        DATE_OF_BIRTH("Date de naissance"),
        PERSON("Personne"),
        NAME("Nom"),
        PERSON_NAME("Nom"),
        FULL_NAME("Nom complet"),
        FIRST_NAME("Prénom"),
        MIDDLE_NAME("Deuxième prénom"),
        LAST_NAME("Nom de famille"),
        SURNAME("Nom de famille"),
        USERNAME("Identifiant système ou compte de connexion"),
        GENDER("Genre"),
        NATIONALITY("Nationalité"),
        MARITAL_STATUS("État civil"),
        EYE_COLOR("Couleur des yeux"),
        NRP("Nationalité, Religion ou Groupe Politique"),
        TITLE("Titre de civilité"),
        RACE("Origine raciale"),
        ETHNICITY("Origine ethnique"),
        RACE_ETHNICITY("Origine raciale ou ethnique"),
        RELIGION("Religion"),
        RELIGIOUS_BELIEF("Croyance religieuse"),
        SEXUALITY("Orientation sexuelle"),
        POLITICAL_VIEW("Opinion politique"),
        LANGUAGE("Langue"),

        // Location
        LOCATION("Localisation"),
        ADDRESS("Adresse"),
        STREET("Rue"),
        STREET_ADDRESS("Adresse (rue)"),
        CITY("Ville"),
        ZIPCODE(POSTAL_CODE_LABEL),
        ZIP_CODE(POSTAL_CODE_LABEL),
        POSTAL_CODE(POSTAL_CODE_LABEL),
        BUILDINGNUM("Numéro de bâtiment"),
        COUNTRY("Pays"),
        STATE("État/Province"),
        STATE_OR_REGION("Canton ou région"),
        GPS("Coordonnées GPS"),
        BUILDING_NUMBER("Numéro de bâtiment"),
        COUNTY("Comté ou district"),
        POSTCODE(POSTAL_CODE_LABEL),
        COORDINATE("Coordonnées géographiques"),

        // Financial
        CREDIT_CARD("Carte de crédit"),
        CREDIT_CARD_NUMBER("Numéro de carte de crédit"),
        CREDIT_CARD_ISSUER("Émetteur de carte"),
        PAYMENT_CARD("Carte de paiement"),
        CARD_NUMBER("Numéro de carte de paiement"),
        CARD_EXPIRY("Date d'expiration de carte"),
        CARD_CVV("Cryptogramme de carte (CVV/CVC)"),
        CVV("Cryptogramme de carte (CVV/CVC)"),
        PIN("Code PIN"),
        MASKED_NUMBER("Numéro masqué"),
        BANK_ACCOUNT("Compte bancaire"),
        BANK_ACCOUNT_NUMBER("Numéro de compte bancaire"),
        ACCOUNT_NAME("Nom de compte"),
        ACCOUNT_NUMBER("Numéro de compte"),
        ROUTING_NUMBER("Numéro de routage bancaire"),
        BIC_SWIFT("Code BIC/SWIFT"),
        IBAN("Identifiant bancaire international (IBAN)"),
        IBAN_CODE("Identifiant bancaire international (IBAN)"),
        CRYPTO("Adresse de cryptomonnaie"),
        BITCOIN_ADDRESS("Adresse Bitcoin"),
        ETHEREUM_ADDRESS("Adresse Ethereum"),
        LITECOIN_ADDRESS("Adresse Litecoin"),
        AMOUNT("Montant"),
        CURRENCY("Devise"),
        CURRENCY_CODE("Code de devise"),
        CURRENCY_NAME("Nom de devise"),
        CURRENCY_SYMBOL("Symbole monétaire"),
        TAX("Numéro fiscal"),
        TAX_ID("Identifiant fiscal"),
        TAX_NUMBER("Numéro fiscal (IDE/TVA)"),
        INVOICE("Facture"),
        SALARY("Salaire"),
        TRANSACTION("Transaction"),
        CREDIT_DEBIT_CARD("Carte de crédit ou de débit"),
        BANK_ROUTING_NUMBER("Numéro de routage bancaire"),
        SWIFT_BIC("Code BIC/SWIFT"),

        // Government IDs
        SSN("Numéro AVS"),
        AVS_NUMBER("Numéro AVS"),
        SOCIALNUM("Numéro de sécurité sociale"),
        GOVERNMENT_ID("Identifiant officiel"),
        NATIONAL_ID("Carte d'identité"),
        NATIONAL_ID_NUMBER("Numéro de carte d'identité"),
        ID_CARD("Carte d'identité"),
        PASSPORT("Passeport"),
        PASSPORT_NUMBER("Numéro de passeport"),
        DRIVER_LICENSE("Permis de conduire"),
        DRIVER_LICENSE_NUMBER("Numéro de permis de conduire"),
        DRIVERS_LICENSE_NUMBER("Numéro de permis de conduire"),
        LICENSE_NUMBER("Numéro de licence"),
        SOCIAL_SECURITY_NUMBER("Numéro de sécurité sociale"),
        CERTIFICATE_LICENSE_NUMBER("Numéro de certificat ou de licence"),
        UNIQUE_ID("Identifiant unique"),

        // IT & Credentials
        PASSWORD("Mot de passe ou code PIN"),
        API_KEY("Clé API"),
        TOKEN("Jeton"),
        ACCESS_TOKEN("Jeton d'accès"),
        SECRET("Secret"),
        SECRET_KEY("Clé secrète"),
        RECOVERY_CODE("Code de récupération"),
        URL("URL"),
        IP_ADDRESS("Adresse IP"),
        MAC_ADDRESS("Adresse MAC"),
        HOSTNAME("Nom d'hôte"),
        DEVICE("Appareil"),
        DEVICE_ID("Identifiant d'appareil"),
        IMEI("Numéro IMEI"),
        SESSION("Session"),
        SESSION_ID("Identifiant de session"),
        ACCOUNT_ID("Identifiant de compte"),
        SENSITIVE_ACCOUNT_ID("Identifiant de compte sensible"),
        IPV4("Adresse IPv4"),
        IPV6("Adresse IPv6"),
        HTTP_COOKIE("Cookie HTTP"),
        DEVICE_IDENTIFIER("Identifiant d'appareil"),
        USER_NAME("Identifiant système ou compte de connexion"),
        CLIENT_SECRET("Secret client (OAuth)"),
        GITHUB_TOKEN("Jeton GitHub"),
        SOFTWARE_LICENSE("Clé de licence logicielle"),

        // Medical/Healthcare
        MEDICAL("Information médicale"),
        MEDICAL_LICENSE("Licence médicale"),
        PATIENT("Patient"),
        PATIENT_ID("Identifiant patient"),
        DIAGNOSIS("Diagnostic"),
        MEDICATION("Médicament"),
        HEALTH_INSURANCE("Assurance maladie"),
        HEALTH_INSURANCE_NUMBER("Numéro d'assurance maladie"),
        INSURANCE_POLICY_NUMBER("Numéro de police d'assurance"),
        MEDICAL_RECORD_NUMBER("Numéro de dossier médical"),
        DOCTOR("Médecin"),
        HOSPITAL("Hôpital"),
        HEALTH_PLAN_BENEFICIARY_NUMBER("Numéro de bénéficiaire d'assurance santé"),
        BLOOD_TYPE("Groupe sanguin"),

        // Professional
        EMPLOYEE("Employé"),
        JOB_TITLE("Poste"),
        COMPANY("Entreprise"),
        DEPARTMENT("Département"),
        STUDENT("Étudiant"),
        SCHOOL("École"),
        OCCUPATION("Profession"),
        EMPLOYMENT_STATUS("Statut professionnel"),
        EMPLOYEE_ID("Identifiant d'employé"),
        EDUCATION_LEVEL("Niveau d'éducation"),
        ORGANIZATION("Organisation"),
        COMPANY_NAME("Nom d'entreprise"),
        CUSTOMER_ID("Identifiant client"),

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
        VEHICLE_REGISTRATION("Immatriculation de véhicule"),
        LICENSE_PLATE("Plaque d'immatriculation"),
        VIN("Numéro VIN"),
        VEHICLE_VIN("Numéro VIN"),
        VEHICLE_IDENTIFIER("Identifiant de véhicule"),
        PROPERTY("Propriété"),
        INSURANCE("Assurance"),

        // Biometric
        BIOMETRIC("Données biométriques"),
        FINGERPRINT("Empreinte digitale"),
        FACIAL("Reconnaissance faciale"),
        IRIS("Scan iris"),
        VOICE("Empreinte vocale"),
        DNA("ADN"),
        BIOMETRIC_IDENTIFIER("Identifiant biométrique"),

        // Temporal
        DATE("Date"),
        TIME("Heure"),
        TIMESTAMP("Horodatage"),
        DATE_TIME("Horodatage"),
        SENSITIVE_DATE("Date sensible"),
        DOCUMENT_DATE("Date de document"),
        EXPIRATION_DATE("Date d'expiration"),
        TRANSACTION_DATE("Date de transaction"),
        AGE("Âge"),

        // Digital Identity
        SOCIAL_MEDIA("Réseaux sociaux"),
        ONLINE_HANDLE("Pseudonyme"),
        ACCOUNT("Compte"),
        CUSTOMER("Client"),

        // Country-specific (Presidio)
        US_SSN("Numéro de sécurité sociale (US)"),
        US_BANK_NUMBER("Compte bancaire (US)"),
        US_DRIVER_LICENSE("Permis de conduire (US)"),
        US_ITIN("Numéro fiscal ITIN (US)"),
        US_PASSPORT("Passeport (US)"),
        UK_NHS("Numéro NHS (UK)"),
        UK_NINO("Numéro d'assurance nationale (UK)"),
        ES_NIF("NIF (ES)"),
        ES_NIE("NIE (ES)"),
        IT_FISCAL_CODE("Code fiscal (IT)"),
        IT_DRIVER_LICENSE("Permis de conduire (IT)"),
        IT_VAT_CODE("Numéro TVA (IT)"),
        IT_PASSPORT("Passeport (IT)"),
        IT_IDENTITY_CARD("Carte d'identité (IT)"),
        PL_PESEL("Numéro PESEL (PL)"),
        SG_NRIC_FIN("NRIC/FIN (SG)"),
        SG_UEN("UEN (SG)"),
        AU_ABN("ABN (AU)"),
        AU_ACN("ACN (AU)"),
        AU_TFN("Numéro fiscal TFN (AU)"),
        AU_MEDICARE("Medicare (AU)"),
        IN_PAN("PAN (IN)"),
        IN_AADHAAR("Aadhaar (IN)"),
        IN_VEHICLE_REGISTRATION("Immatriculation (IN)"),
        IN_VOTER("Carte d'électeur (IN)"),
        IN_PASSPORT("Passeport (IN)"),
        FI_PERSONAL_IDENTITY_CODE("Code d'identité personnel (FI)"),
        KR_RRN("Numéro RRN (KR)"),
        TH_TNIN("Numéro TNIN (TH)"),

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
        PRESIDIO,
        REGEX,
        /**
         * The deterministic format precision post-filter, surfaced as a pseudo
         * detector in run-stats only to expose how many PII it discarded.
         */
        POSTFILTER,
        /**
         * Specialised LLM PII detector (Ministral-PII).
         */
        MINISTRAL
    }

    /**
     * Represents a single sensitive element detected on the page.
     *
     * @param type business category of the detected element (string key, e.g. "EMAIL")
     * @param typeLabel human-readable label for the type (e.g. "Email")
     * @param value raw value as found in the content
     * @param context short surrounding text to help understand the occurrence
     * @param startingPosition start index of the occurrence in the content
     * @param endingPosition endingPosition index of the occurrence in the content
     * @param confidenceScore confidence confidenceScore provided by the detector (may be null)
     * @param selector optional selector or hint pointing to the element location
     * @param source source of the detected PII entity (e.g. Presidio, Ministral)
     */
    public record SensitiveData(
        String type,
        String typeLabel,
        String value,
        String context,
        int startingPosition,
        int endingPosition,
        Double confidenceScore,
        String selector,
        DetectorSource source
    ) {
    }

    /**
     * Execution stats of a single detector for one analysis request.
     *
     * <p>Detectors run sequentially within a request, so {@code durationMs} is
     * the real time spent by this detector and is safe to sum per detector
     * across requests to obtain cumulated busy time.
     *
     * @param source the detector these stats belong to
     * @param durationMs wall-clock duration of this detector's execution, in milliseconds
     * @param entitiesFound for real detectors, raw entities found (pre-merge); for the
     *                      post-filter, the number of PII examined
     * @param entitiesDiscarded number of PII the stage discarded (0 for real detectors)
     * @param error short technical reason why this detector produced nothing usable
     *              (e.g. its remote endpoint was unreachable), empty when it ran
     *              normally; without it a failed detector is indistinguishable from
     *              one that legitimately found no PII
     */
    public record DetectorRunStat(
        DetectorSource source,
        long durationMs,
        int entitiesFound,
        int entitiesDiscarded,
        String error
    ) {

        /**
         * Whether this detector failed to run for the analysed content.
         *
         * @return true when a failure reason was reported
         */
        public boolean failed() {
            return error != null && !error.isBlank();
        }
    }

    /**
     * A sensitive element detected by the detectors but discarded by the
     * precision post-filter.
     *
     * @param data the element as originally detected (before rejection)
     * @param judgeVerdict verdict that motivated the rejection (e.g. FALSE_POSITIVE)
     * @param judgeConfidence post-filter confidence in the verdict (0.0-1.0)
     * @param judgeReason short natural-language justification for the rejection
     */
    public record DiscardedSensitiveData(
        SensitiveData data,
        String judgeVerdict,
        Double judgeConfidence,
        String judgeReason
    ) {
    }
}