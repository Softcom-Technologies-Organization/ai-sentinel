package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;

/**
 * Enumeration of PII (Personally Identifiable Information) types emitted by the gRPC server.
 * Aligned with server taxonomy from Multi-Pass GLiNER detector and Presidio.
 *
 * CONSOLIDATED VERSION: 44 PII types across 7 categories
 * Down from 114 types / 13 categories for better performance and accuracy.
 *
 * Categories:
 * 1. IDENTITY - 9 types (core personal identity)
 * 2. CONTACT - 4 types (contact information)
 * 3. DIGITAL - 3 types (online identifiers)
 * 4. FINANCIAL - 6 types (money/banking)
 * 5. MEDICAL - 6 types (health info)
 * 6. IT_CREDENTIALS - 9 types (technical/secrets)
 * 7. LEGAL_ASSET - 7 types (legal + property)
 */
public enum PersonallyIdentifiableInformationType {

    // =========================================================================
    // Category 1: IDENTITY - Core personal identity
    // =========================================================================
    PERSON_NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.NAME),
    NATIONAL_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    SSN(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    PASSPORT_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSPORT),
    DRIVER_LICENSE_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.DRIVER_LICENSE),
    DATE_OF_BIRTH(ContentPiiDetection.PersonallyIdentifiableInformationType.DATE_OF_BIRTH),
    GENDER(ContentPiiDetection.PersonallyIdentifiableInformationType.GENDER),
    NATIONALITY(ContentPiiDetection.PersonallyIdentifiableInformationType.NATIONALITY),
    AGE(ContentPiiDetection.PersonallyIdentifiableInformationType.AGE),
    NRP(ContentPiiDetection.PersonallyIdentifiableInformationType.NRP),

    // =========================================================================
    // Category 2: CONTACT - Contact information
    // =========================================================================
    EMAIL(ContentPiiDetection.PersonallyIdentifiableInformationType.EMAIL),
    PHONE_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.PHONE),
    ADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.ADDRESS),
    POSTAL_CODE(ContentPiiDetection.PersonallyIdentifiableInformationType.ZIPCODE),

    // =========================================================================
    // Category 3: DIGITAL - Online identifiers
    // =========================================================================
    USERNAME(ContentPiiDetection.PersonallyIdentifiableInformationType.USERNAME),
    ACCOUNT_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.ACCOUNT),
    URL(ContentPiiDetection.PersonallyIdentifiableInformationType.URL),

    // =========================================================================
    // Category 4: FINANCIAL - Banking and payment
    // =========================================================================
    CREDIT_CARD_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.CREDIT_CARD),
    BANK_ACCOUNT_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    IBAN(ContentPiiDetection.PersonallyIdentifiableInformationType.IBAN),
    BIC_SWIFT(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    TAX_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    SALARY(ContentPiiDetection.PersonallyIdentifiableInformationType.SALARY),

    // =========================================================================
    // Category 5: MEDICAL - Health information
    // =========================================================================
    AVS_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    PATIENT_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.PATIENT),
    MEDICAL_RECORD_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICAL),
    HEALTH_INSURANCE_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.HEALTH_INSURANCE),
    DIAGNOSIS(ContentPiiDetection.PersonallyIdentifiableInformationType.DIAGNOSIS),
    MEDICATION(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICATION),

    // =========================================================================
    // Category 6: IT_CREDENTIALS - Technical identifiers and secrets
    // =========================================================================
    IP_ADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.IP_ADDRESS),
    MAC_ADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.MAC_ADDRESS),
    HOSTNAME(ContentPiiDetection.PersonallyIdentifiableInformationType.HOSTNAME),
    DEVICE_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.DEVICE),
    PASSWORD(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSWORD),
    API_KEY(ContentPiiDetection.PersonallyIdentifiableInformationType.API_KEY),
    ACCESS_TOKEN(ContentPiiDetection.PersonallyIdentifiableInformationType.TOKEN),
    SECRET_KEY(ContentPiiDetection.PersonallyIdentifiableInformationType.SECRET),
    SESSION_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.SESSION),

    // =========================================================================
    // Category 7: LEGAL_ASSET - Legal documents and property (7 types)
    // =========================================================================
    CASE_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.CASE_NUMBER),
    LICENSE_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.LICENSE),
    CRIMINAL_RECORD(ContentPiiDetection.PersonallyIdentifiableInformationType.CRIMINAL_RECORD),
    VEHICLE_REGISTRATION(ContentPiiDetection.PersonallyIdentifiableInformationType.VEHICLE),
    LICENSE_PLATE(ContentPiiDetection.PersonallyIdentifiableInformationType.LICENSE_PLATE),
    VIN(ContentPiiDetection.PersonallyIdentifiableInformationType.VIN),
    INSURANCE_POLICY_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.INSURANCE),

    // =========================================================================
    // Presidio / Country Specific Types
    // These are used by the Presidio detector
    // =========================================================================
    
    // Country-specific (Presidio)
    US_SSN(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    US_PASSPORT(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSPORT),
    US_DRIVER_LICENSE(ContentPiiDetection.PersonallyIdentifiableInformationType.DRIVER_LICENSE),
    US_BANK_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    AU_MEDICARE(ContentPiiDetection.PersonallyIdentifiableInformationType.HEALTH_INSURANCE),
    IN_AADHAAR(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),

    // Presidio specific types
    EMAIL_ADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.EMAIL),
    IBAN_CODE(ContentPiiDetection.PersonallyIdentifiableInformationType.IBAN),
    CRYPTO(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    PERSON(ContentPiiDetection.PersonallyIdentifiableInformationType.PERSON),
    DATE_TIME(ContentPiiDetection.PersonallyIdentifiableInformationType.TIMESTAMP),
    MEDICAL_LICENSE(ContentPiiDetection.PersonallyIdentifiableInformationType.LICENSE),
    CREDIT_CARD(ContentPiiDetection.PersonallyIdentifiableInformationType.CREDIT_CARD),
    LOCATION(ContentPiiDetection.PersonallyIdentifiableInformationType.LOCATION),
    
    // Country specific Presidio types
    US_ITIN(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    UK_NHS(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICAL),
    UK_NINO(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    ES_NIF(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    ES_NIE(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    IT_FISCAL_CODE(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    IT_DRIVER_LICENSE(ContentPiiDetection.PersonallyIdentifiableInformationType.DRIVER_LICENSE),
    IT_VAT_CODE(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    IT_PASSPORT(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSPORT),
    IT_IDENTITY_CARD(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    PL_PESEL(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    SG_NRIC_FIN(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    SG_UEN(ContentPiiDetection.PersonallyIdentifiableInformationType.COMPANY),
    AU_ABN(ContentPiiDetection.PersonallyIdentifiableInformationType.COMPANY),
    AU_ACN(ContentPiiDetection.PersonallyIdentifiableInformationType.COMPANY),
    AU_TFN(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    IN_PAN(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    IN_VEHICLE_REGISTRATION(ContentPiiDetection.PersonallyIdentifiableInformationType.VEHICLE),
    IN_VOTER(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    IN_PASSPORT(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSPORT),
    FI_PERSONAL_IDENTITY_CODE(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    KR_RRN(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    TH_TNIN(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),

    // Unknown fallback
    UNKNOWN(ContentPiiDetection.PersonallyIdentifiableInformationType.UNKNOWN);

    private final ContentPiiDetection.PersonallyIdentifiableInformationType dataType;

    PersonallyIdentifiableInformationType(
        ContentPiiDetection.PersonallyIdentifiableInformationType dataType) {
        this.dataType = dataType;
    }

    public ContentPiiDetection.PersonallyIdentifiableInformationType dataType() {
        return dataType;
    }
}