package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;

/**
 * Enumeration of PII (Personally Identifiable Information) types emitted by the gRPC server.
 * Aligned with server taxonomy from Multi-Pass GLiNER detector.
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
    // Category 1: IDENTITY - Core personal identity (9 types)
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

    // =========================================================================
    // Category 2: CONTACT - Contact information (4 types)
    // =========================================================================
    EMAIL(ContentPiiDetection.PersonallyIdentifiableInformationType.EMAIL),
    PHONE_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.PHONE),
    ADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.ADDRESS),
    POSTAL_CODE(ContentPiiDetection.PersonallyIdentifiableInformationType.ZIPCODE),

    // =========================================================================
    // Category 3: DIGITAL - Online identifiers (3 types)
    // =========================================================================
    USERNAME(ContentPiiDetection.PersonallyIdentifiableInformationType.USERNAME),
    ACCOUNT_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.ACCOUNT),
    URL(ContentPiiDetection.PersonallyIdentifiableInformationType.URL),

    // =========================================================================
    // Category 4: FINANCIAL - Banking and payment (6 types)
    // =========================================================================
    CREDIT_CARD_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.CREDIT_CARD),
    BANK_ACCOUNT_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    IBAN(ContentPiiDetection.PersonallyIdentifiableInformationType.IBAN),
    BIC_SWIFT(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    TAX_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    SALARY(ContentPiiDetection.PersonallyIdentifiableInformationType.SALARY),

    // =========================================================================
    // Category 5: MEDICAL - Health information (6 types)
    // =========================================================================
    AVS_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    PATIENT_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.PATIENT),
    MEDICAL_RECORD_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICAL),
    HEALTH_INSURANCE_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.HEALTH_INSURANCE),
    DIAGNOSIS(ContentPiiDetection.PersonallyIdentifiableInformationType.DIAGNOSIS),
    MEDICATION(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICATION),

    // =========================================================================
    // Category 6: IT_CREDENTIALS - Technical identifiers and secrets (9 types)
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
    // Legacy types for backward compatibility
    // These map to the new consolidated types
    // =========================================================================
    // Name variants -> PERSON_NAME
    FIRST_NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.NAME),
    LAST_NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.SURNAME),
    FULL_NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.NAME),
    NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.NAME),
    GIVENNAME(ContentPiiDetection.PersonallyIdentifiableInformationType.NAME),
    SURNAME(ContentPiiDetection.PersonallyIdentifiableInformationType.SURNAME),

    // Phone variants -> PHONE_NUMBER
    MOBILE_PHONE(ContentPiiDetection.PersonallyIdentifiableInformationType.PHONE),
    PHONE(ContentPiiDetection.PersonallyIdentifiableInformationType.PHONE),
    TELEPHONENUM(ContentPiiDetection.PersonallyIdentifiableInformationType.PHONE),

    // Address variants -> ADDRESS
    HOME_ADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.ADDRESS),
    MAILING_ADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.ADDRESS),
    STREET(ContentPiiDetection.PersonallyIdentifiableInformationType.STREET),
    LOCATION(ContentPiiDetection.PersonallyIdentifiableInformationType.LOCATION),

    // ID variants -> NATIONAL_ID
    ID_CARD_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    IDCARDNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),

    // Card variants -> CREDIT_CARD_NUMBER
    DEBIT_CARD_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.CREDIT_CARD),
    CREDITCARDNUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.CREDIT_CARD),
    CREDIT_CARD(ContentPiiDetection.PersonallyIdentifiableInformationType.CREDIT_CARD),

    // Bank variants -> BANK_ACCOUNT_NUMBER
    ACCOUNTNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    BANKACCOUNT(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    BANK_ACCOUNT(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),

    // SSN variants
    SOCIALNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    US_SSN(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    AVSNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),

    // Date variants -> DATE_OF_BIRTH
    BIRTH_DATE(ContentPiiDetection.PersonallyIdentifiableInformationType.DATE_OF_BIRTH),
    DATEOFBIRTH(ContentPiiDetection.PersonallyIdentifiableInformationType.DATE_OF_BIRTH),
    DOB(ContentPiiDetection.PersonallyIdentifiableInformationType.DATE_OF_BIRTH),

    // License variants
    DRIVERLICENSENUM(ContentPiiDetection.PersonallyIdentifiableInformationType.DRIVER_LICENSE),
    DRIVER_LICENSE(ContentPiiDetection.PersonallyIdentifiableInformationType.DRIVER_LICENSE),
    PASSPORT(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSPORT),
    PASSPORTNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSPORT),

    // IT legacy
    IPADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.IP_ADDRESS),
    MACADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.MAC_ADDRESS),
    TOKEN(ContentPiiDetection.PersonallyIdentifiableInformationType.TOKEN),

    // Location legacy
    CITY(ContentPiiDetection.PersonallyIdentifiableInformationType.CITY),
    STATE(ContentPiiDetection.PersonallyIdentifiableInformationType.STATE),
    COUNTRY(ContentPiiDetection.PersonallyIdentifiableInformationType.COUNTRY),
    ZIPCODE(ContentPiiDetection.PersonallyIdentifiableInformationType.ZIPCODE),
    BUILDINGNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.BUILDINGNUM),

    // Tax legacy
    TAXNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),

    // Medical legacy
    HEALTH_INSURANCE(ContentPiiDetection.PersonallyIdentifiableInformationType.HEALTH_INSURANCE),
    MEDICAL_RECORD(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICAL),

    // Country-specific legacy (commonly used)
    US_PASSPORT(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSPORT),
    US_DRIVER_LICENSE(ContentPiiDetection.PersonallyIdentifiableInformationType.DRIVER_LICENSE),
    US_BANK_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    AU_MEDICARE(ContentPiiDetection.PersonallyIdentifiableInformationType.HEALTH_INSURANCE),
    IN_AADHAAR(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),

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
