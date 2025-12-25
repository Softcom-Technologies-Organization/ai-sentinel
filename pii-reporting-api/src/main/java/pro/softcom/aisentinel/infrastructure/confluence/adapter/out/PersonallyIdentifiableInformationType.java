package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;

/**
 * Enumeration of PII (Personally Identifiable Information) types emitted by the gRPC server.
 * Aligned with server taxonomy including types from Multi-Pass GLiNER detector.
 *
 * What: Each enum constant carries its business-level ContentPiiDetection.PersonallyIdentifiableInformationType mapping.
 * How to use: Clients must parse detector labels strictly with {@code PiiType.valueOf(label)}
 * (case-insensitive via upper-casing) and retrieve {@link #dataType()}.
 *
 * Total: 114 PII types across 13 categories mapped to domain categories.
 */
public enum PersonallyIdentifiableInformationType {

    // =========================================================================
    // Category 1: IDENTITY - Core personal identity (14 types)
    // =========================================================================
    PERSON_NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.NAME),
    FIRST_NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.NAME),
    LAST_NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.SURNAME),
    FULL_NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.NAME),
    NATIONAL_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    SSN(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    PASSPORT_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSPORT),
    DRIVER_LICENSE_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.DRIVER_LICENSE),
    ID_CARD_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    BIRTH_DATE(ContentPiiDetection.PersonallyIdentifiableInformationType.DATE_OF_BIRTH),
    PLACE_OF_BIRTH(ContentPiiDetection.PersonallyIdentifiableInformationType.LOCATION),
    GENDER(ContentPiiDetection.PersonallyIdentifiableInformationType.GENDER),
    NATIONALITY(ContentPiiDetection.PersonallyIdentifiableInformationType.NATIONALITY),
    MARITAL_STATUS(ContentPiiDetection.PersonallyIdentifiableInformationType.MARITAL_STATUS),

    // =========================================================================
    // Category 2: CONTACT - How to reach a person (11 types)
    // =========================================================================
    EMAIL(ContentPiiDetection.PersonallyIdentifiableInformationType.EMAIL),
    PHONE_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.PHONE),
    MOBILE_PHONE(ContentPiiDetection.PersonallyIdentifiableInformationType.PHONE),
    FAX_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.FAX),
    HOME_ADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.ADDRESS),
    MAILING_ADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.ADDRESS),
    POSTAL_CODE(ContentPiiDetection.PersonallyIdentifiableInformationType.ZIPCODE),
    CITY(ContentPiiDetection.PersonallyIdentifiableInformationType.CITY),
    STATE(ContentPiiDetection.PersonallyIdentifiableInformationType.STATE),
    COUNTRY(ContentPiiDetection.PersonallyIdentifiableInformationType.COUNTRY),
    PO_BOX(ContentPiiDetection.PersonallyIdentifiableInformationType.ADDRESS),

    // =========================================================================
    // Category 3: DIGITAL_IDENTITY - Online identifiers (8 types)
    // =========================================================================
    USERNAME(ContentPiiDetection.PersonallyIdentifiableInformationType.USERNAME),
    LOGIN(ContentPiiDetection.PersonallyIdentifiableInformationType.USERNAME),
    ONLINE_HANDLE(ContentPiiDetection.PersonallyIdentifiableInformationType.ONLINE_HANDLE),
    SOCIAL_MEDIA_HANDLE(ContentPiiDetection.PersonallyIdentifiableInformationType.SOCIAL_MEDIA),
    SOCIAL_MEDIA_URL(ContentPiiDetection.PersonallyIdentifiableInformationType.SOCIAL_MEDIA),
    USER_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.ACCOUNT),
    ACCOUNT_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.ACCOUNT),
    CUSTOMER_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.CUSTOMER),

    // =========================================================================
    // Category 4: FINANCIAL - Banking and payment (12 types)
    // =========================================================================
    CREDIT_CARD_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.CREDIT_CARD),
    DEBIT_CARD_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.CREDIT_CARD),
    BANK_ACCOUNT_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    IBAN(ContentPiiDetection.PersonallyIdentifiableInformationType.IBAN),
    BIC_SWIFT(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    ROUTING_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    TAX_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    VAT_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    PAYMENT_REFERENCE(ContentPiiDetection.PersonallyIdentifiableInformationType.TRANSACTION),
    INVOICE_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.INVOICE),
    SALARY(ContentPiiDetection.PersonallyIdentifiableInformationType.SALARY),
    TRANSACTION_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.TRANSACTION),

    // =========================================================================
    // Category 5: MEDICAL - Health information (11 types)
    // =========================================================================
    AVS_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    PATIENT_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.PATIENT),
    MEDICAL_RECORD_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICAL),
    HEALTH_INSURANCE_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.HEALTH_INSURANCE),
    DIAGNOSIS(ContentPiiDetection.PersonallyIdentifiableInformationType.DIAGNOSIS),
    MEDICAL_CONDITION(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICAL),
    MEDICATION(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICATION),
    PRESCRIPTION(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICATION),
    TREATMENT(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICAL),
    DOCTOR_NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.DOCTOR),
    HOSPITAL_NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.HOSPITAL),

    // =========================================================================
    // Category 6: PROFESSIONAL - Employment and education (10 types)
    // =========================================================================
    EMPLOYEE_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.EMPLOYEE),
    EMPLOYEE_NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.EMPLOYEE),
    JOB_TITLE(ContentPiiDetection.PersonallyIdentifiableInformationType.JOB_TITLE),
    DEPARTMENT(ContentPiiDetection.PersonallyIdentifiableInformationType.DEPARTMENT),
    COMPANY_NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.COMPANY),
    WORK_EMAIL(ContentPiiDetection.PersonallyIdentifiableInformationType.EMAIL),
    WORK_PHONE(ContentPiiDetection.PersonallyIdentifiableInformationType.PHONE),
    STUDENT_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.STUDENT),
    SCHOOL_NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.SCHOOL),
    UNIVERSITY_NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.SCHOOL),

    // =========================================================================
    // Category 7: LOCATION - Precise location (5 types)
    // =========================================================================
    GPS_COORDINATES(ContentPiiDetection.PersonallyIdentifiableInformationType.GPS),
    HOME_LOCATION(ContentPiiDetection.PersonallyIdentifiableInformationType.LOCATION),
    WORK_LOCATION(ContentPiiDetection.PersonallyIdentifiableInformationType.LOCATION),
    CURRENT_LOCATION(ContentPiiDetection.PersonallyIdentifiableInformationType.LOCATION),
    ADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.ADDRESS),

    // =========================================================================
    // Category 8: IT - Technical identifiers and credentials (19 types)
    // =========================================================================
    IP_ADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.IP_ADDRESS),
    MAC_ADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.MAC_ADDRESS),
    HOSTNAME(ContentPiiDetection.PersonallyIdentifiableInformationType.HOSTNAME),
    DEVICE_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.DEVICE),
    SERIAL_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.DEVICE),
    IMEI(ContentPiiDetection.PersonallyIdentifiableInformationType.DEVICE),
    IMSI(ContentPiiDetection.PersonallyIdentifiableInformationType.DEVICE),
    SESSION_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.SESSION),
    COOKIE_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.SESSION),
    USER_AGENT(ContentPiiDetection.PersonallyIdentifiableInformationType.DEVICE),
    PASSWORD(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSWORD),
    PASSWORD_HASH(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSWORD),
    API_KEY(ContentPiiDetection.PersonallyIdentifiableInformationType.API_KEY),
    ACCESS_TOKEN(ContentPiiDetection.PersonallyIdentifiableInformationType.TOKEN),
    REFRESH_TOKEN(ContentPiiDetection.PersonallyIdentifiableInformationType.TOKEN),
    SECRET_KEY(ContentPiiDetection.PersonallyIdentifiableInformationType.SECRET),
    PRIVATE_KEY(ContentPiiDetection.PersonallyIdentifiableInformationType.SECRET),
    SSH_KEY(ContentPiiDetection.PersonallyIdentifiableInformationType.SECRET),
    OAUTH_TOKEN(ContentPiiDetection.PersonallyIdentifiableInformationType.TOKEN),

    // =========================================================================
    // Category 9: RESOURCE - Online resources (5 types)
    // =========================================================================
    URL(ContentPiiDetection.PersonallyIdentifiableInformationType.URL),
    PROFILE_URL(ContentPiiDetection.PersonallyIdentifiableInformationType.URL),
    PERSONAL_WEBSITE(ContentPiiDetection.PersonallyIdentifiableInformationType.URL),
    API_ENDPOINT(ContentPiiDetection.PersonallyIdentifiableInformationType.URL),
    FILE_PATH(ContentPiiDetection.PersonallyIdentifiableInformationType.URL),

    // =========================================================================
    // Category 10: TEMPORAL - Dates and time (4 types)
    // =========================================================================
    DATE(ContentPiiDetection.PersonallyIdentifiableInformationType.DATE),
    TIME(ContentPiiDetection.PersonallyIdentifiableInformationType.TIME),
    TIMESTAMP(ContentPiiDetection.PersonallyIdentifiableInformationType.TIMESTAMP),
    AGE(ContentPiiDetection.PersonallyIdentifiableInformationType.AGE),

    // =========================================================================
    // Category 11: BIOMETRIC - Biometric data (5 types)
    // =========================================================================
    FINGERPRINT(ContentPiiDetection.PersonallyIdentifiableInformationType.FINGERPRINT),
    FACIAL_RECOGNITION_DATA(ContentPiiDetection.PersonallyIdentifiableInformationType.FACIAL),
    IRIS_SCAN(ContentPiiDetection.PersonallyIdentifiableInformationType.IRIS),
    VOICE_PRINT(ContentPiiDetection.PersonallyIdentifiableInformationType.VOICE),
    DNA_SEQUENCE(ContentPiiDetection.PersonallyIdentifiableInformationType.DNA),

    // =========================================================================
    // Category 12: LEGAL - Legal and government (6 types)
    // =========================================================================
    CASE_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.CASE_NUMBER),
    COURT_NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.COURT),
    CRIMINAL_RECORD(ContentPiiDetection.PersonallyIdentifiableInformationType.CRIMINAL_RECORD),
    LICENSE_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.LICENSE),
    PERMIT_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.PERMIT),
    IMMIGRATION_STATUS(ContentPiiDetection.PersonallyIdentifiableInformationType.IMMIGRATION),

    // =========================================================================
    // Category 13: ASSET - Vehicles and property (5 types)
    // =========================================================================
    VEHICLE_REGISTRATION(ContentPiiDetection.PersonallyIdentifiableInformationType.VEHICLE),
    LICENSE_PLATE(ContentPiiDetection.PersonallyIdentifiableInformationType.LICENSE_PLATE),
    VIN(ContentPiiDetection.PersonallyIdentifiableInformationType.VIN),
    PROPERTY_ID(ContentPiiDetection.PersonallyIdentifiableInformationType.PROPERTY),
    INSURANCE_POLICY_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.INSURANCE),

    // =========================================================================
    // Legacy types for backward compatibility
    // =========================================================================
    // Original ML detector types
    ACCOUNTNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    BANKACCOUNT(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    BUILDINGNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.BUILDINGNUM),
    CREDITCARDNUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.CREDIT_CARD),
    CREDITCARD(ContentPiiDetection.PersonallyIdentifiableInformationType.CREDIT_CARD),
    CREDITCARDEXP(ContentPiiDetection.PersonallyIdentifiableInformationType.CREDIT_CARD),
    CVV(ContentPiiDetection.PersonallyIdentifiableInformationType.CREDIT_CARD),
    DATEOFBIRTH(ContentPiiDetection.PersonallyIdentifiableInformationType.DATE_OF_BIRTH),
    DOB(ContentPiiDetection.PersonallyIdentifiableInformationType.DATE_OF_BIRTH),
    DRIVERLICENSENUM(ContentPiiDetection.PersonallyIdentifiableInformationType.DRIVER_LICENSE),
    DRIVERLICENSE(ContentPiiDetection.PersonallyIdentifiableInformationType.DRIVER_LICENSE),
    GIVENNAME(ContentPiiDetection.PersonallyIdentifiableInformationType.NAME),
    IDCARDNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    SOCIALNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    SSN_SHORT(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    ROUTINGNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    STREET(ContentPiiDetection.PersonallyIdentifiableInformationType.STREET),
    SURNAME(ContentPiiDetection.PersonallyIdentifiableInformationType.SURNAME),
    TAXNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    TELEPHONENUM(ContentPiiDetection.PersonallyIdentifiableInformationType.PHONE),
    ZIPCODE(ContentPiiDetection.PersonallyIdentifiableInformationType.ZIPCODE),
    LOCATIONZIP(ContentPiiDetection.PersonallyIdentifiableInformationType.ZIPCODE),

    // Location variants
    NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.NAME),
    LOCATIONADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.LOCATION),
    LOCATIONSTREET(ContentPiiDetection.PersonallyIdentifiableInformationType.STREET),
    LOCATIONCITY(ContentPiiDetection.PersonallyIdentifiableInformationType.CITY),
    LOCATIONSTATE(ContentPiiDetection.PersonallyIdentifiableInformationType.STATE),
    LOCATIONCOUNTRY(ContentPiiDetection.PersonallyIdentifiableInformationType.COUNTRY),
    LOCATION(ContentPiiDetection.PersonallyIdentifiableInformationType.LOCATION),

    // Healthcare legacy
    MEDICALPROFNAME(ContentPiiDetection.PersonallyIdentifiableInformationType.DOCTOR),
    HEALTHCARENUM(ContentPiiDetection.PersonallyIdentifiableInformationType.HEALTH_INSURANCE),
    MEDICALCONDITION(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICAL),
    MEDICALPROCESS(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICAL),
    DRUG(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICATION),
    DOSE(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICATION),
    BLOODTYPE(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICAL),
    INJURY(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICAL),
    MEDICALFACILITY(ContentPiiDetection.PersonallyIdentifiableInformationType.HOSPITAL),
    MEDICALCODE(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICAL),
    MEDICAL_RECORD(ContentPiiDetection.PersonallyIdentifiableInformationType.MEDICAL),
    HEALTH_INSURANCE(ContentPiiDetection.PersonallyIdentifiableInformationType.HEALTH_INSURANCE),

    // IDs legacy
    PASSPORTNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSPORT),
    PASSPORT(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSPORT),
    DRIVER_LICENSE(ContentPiiDetection.PersonallyIdentifiableInformationType.DRIVER_LICENSE),
    VEHICLEID(ContentPiiDetection.PersonallyIdentifiableInformationType.VEHICLE),

    // Financial legacy
    MONEY(ContentPiiDetection.PersonallyIdentifiableInformationType.SALARY),
    CREDIT_CARD(ContentPiiDetection.PersonallyIdentifiableInformationType.CREDIT_CARD),
    BANK_ACCOUNT(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    CRYPTO_WALLET(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),

    // Contact legacy
    PHONE(ContentPiiDetection.PersonallyIdentifiableInformationType.PHONE),
    EMAIL_ADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.EMAIL),

    // IT legacy
    IPADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.IP_ADDRESS),
    MACADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.MAC_ADDRESS),
    TOKEN(ContentPiiDetection.PersonallyIdentifiableInformationType.TOKEN),
    JWT_TOKEN(ContentPiiDetection.PersonallyIdentifiableInformationType.TOKEN),
    GITHUB_TOKEN(ContentPiiDetection.PersonallyIdentifiableInformationType.TOKEN),
    AWS_KEY(ContentPiiDetection.PersonallyIdentifiableInformationType.API_KEY),
    SECRETACCESSKEY(ContentPiiDetection.PersonallyIdentifiableInformationType.SECRET),
    CONNECTION_STRING(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSWORD),

    // Country-specific legacy types
    // USA
    US_BANK_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    US_DRIVER_LICENSE(ContentPiiDetection.PersonallyIdentifiableInformationType.DRIVER_LICENSE),
    US_ITIN(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    US_PASSPORT(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSPORT),
    US_SSN(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),

    // UK
    UK_NHS(ContentPiiDetection.PersonallyIdentifiableInformationType.HEALTH_INSURANCE),
    UK_NINO(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    NHS_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.HEALTH_INSURANCE),

    // Spain
    ES_NIF(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    ES_NIE(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),

    // Italy
    IT_FISCAL_CODE(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    IT_DRIVER_LICENSE(ContentPiiDetection.PersonallyIdentifiableInformationType.DRIVER_LICENSE),
    IT_VAT_CODE(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    IT_PASSPORT(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSPORT),
    IT_IDENTITY_CARD(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),

    // Poland
    PL_PESEL(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),

    // Singapore
    SG_NRIC_FIN(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    SG_UEN(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    NRIC(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),

    // Australia
    AU_ABN(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    AU_ACN(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    AU_TFN(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    AU_MEDICARE(ContentPiiDetection.PersonallyIdentifiableInformationType.HEALTH_INSURANCE),
    ABN(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    ACN(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    TFN(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    MEDICARE(ContentPiiDetection.PersonallyIdentifiableInformationType.HEALTH_INSURANCE),

    // India
    IN_PAN(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    IN_AADHAAR(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    IN_VEHICLE_REGISTRATION(ContentPiiDetection.PersonallyIdentifiableInformationType.VEHICLE),
    IN_VOTER(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    IN_PASSPORT(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSPORT),
    VEHICLEREG(ContentPiiDetection.PersonallyIdentifiableInformationType.VEHICLE),
    VOTERID(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),

    // Other countries
    FI_PERSONAL_IDENTITY_CODE(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    KR_RRN(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    TH_TNIN(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),

    // Swiss
    AVSNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),

    // Presidio types
    MEDICAL_LICENSE(ContentPiiDetection.PersonallyIdentifiableInformationType.LICENSE),
    ITIN(ContentPiiDetection.PersonallyIdentifiableInformationType.TAX),
    NRP(ContentPiiDetection.PersonallyIdentifiableInformationType.PERSON),
    DATE_OF_BIRTH(ContentPiiDetection.PersonallyIdentifiableInformationType.DATE_OF_BIRTH),

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
