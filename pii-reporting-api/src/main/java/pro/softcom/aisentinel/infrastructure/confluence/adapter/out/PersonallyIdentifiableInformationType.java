package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;

/**
 * Enumeration of PII (Personally Identifiable Information) types emitted by the gRPC server.
 * Aligned with server taxonomy including types from ML detector, Presidio, and Regex detectors.
 *
 * What: Each enum constant carries its business-level ContentAnalysis.DataType mapping.
 * How to use: Clients must parse detector labels strictly with {@code PiiType.valueOf(label)}
 * (case-insensitive via upper-casing) and retrieve {@link #dataType()}.
 * No aliasing or fallback mapping is provided here to keep tests and production aligned.
 */
public enum PersonallyIdentifiableInformationType {
    // Original types from ML detector
    ACCOUNTNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    BUILDINGNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.BUILDINGNUM),
    CITY(ContentPiiDetection.PersonallyIdentifiableInformationType.CITY),
    CREDITCARDNUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.CREDIT_CARD),
    DATEOFBIRTH(ContentPiiDetection.PersonallyIdentifiableInformationType.PERSON), // closest business category
    DRIVERLICENSENUM(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    EMAIL(ContentPiiDetection.PersonallyIdentifiableInformationType.EMAIL),
    GIVENNAME(ContentPiiDetection.PersonallyIdentifiableInformationType.NAME),
    IDCARDNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    PASSWORD(ContentPiiDetection.PersonallyIdentifiableInformationType.PASSWORD),
    SOCIALNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    STREET(ContentPiiDetection.PersonallyIdentifiableInformationType.STREET),
    SURNAME(ContentPiiDetection.PersonallyIdentifiableInformationType.SURNAME),
    TAXNUM(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN), // treated as sensitive identifier
    TELEPHONENUM(ContentPiiDetection.PersonallyIdentifiableInformationType.PHONE),
    USERNAME(ContentPiiDetection.PersonallyIdentifiableInformationType.USERNAME),
    ZIPCODE(ContentPiiDetection.PersonallyIdentifiableInformationType.ZIPCODE),
    
    // Additional types from Presidio and Regex detectors
    PHONE(ContentPiiDetection.PersonallyIdentifiableInformationType.PHONE),
    URL(ContentPiiDetection.PersonallyIdentifiableInformationType.URL),
    CREDIT_CARD(ContentPiiDetection.PersonallyIdentifiableInformationType.CREDIT_CARD),
    IBAN(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    CRYPTO_WALLET(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    SSN(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    NHS_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    NRIC(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    ABN(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    ACN(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    TFN(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    MEDICARE(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    IP_ADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.IP_ADDRESS),
    MAC_ADDRESS(ContentPiiDetection.PersonallyIdentifiableInformationType.IP_ADDRESS),
    PERSON_NAME(ContentPiiDetection.PersonallyIdentifiableInformationType.NAME),
    LOCATION(ContentPiiDetection.PersonallyIdentifiableInformationType.LOCATION),
    DATE(ContentPiiDetection.PersonallyIdentifiableInformationType.PERSON),
    AGE(ContentPiiDetection.PersonallyIdentifiableInformationType.PERSON),
    MEDICAL_LICENSE(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    PASSPORT(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    DRIVER_LICENSE(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    ITIN(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    NRP(ContentPiiDetection.PersonallyIdentifiableInformationType.PERSON),
    
    // USA specific
    US_BANK_NUMBER(ContentPiiDetection.PersonallyIdentifiableInformationType.BANK_ACCOUNT),
    US_DRIVER_LICENSE(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    US_ITIN(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    US_PASSPORT(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    US_SSN(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    
    // UK specific
    UK_NHS(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    UK_NINO(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    
    // Spain specific
    ES_NIF(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    ES_NIE(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    
    // Italy specific
    IT_FISCAL_CODE(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    IT_DRIVER_LICENSE(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    IT_VAT_CODE(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    IT_PASSPORT(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    IT_IDENTITY_CARD(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    
    // Poland specific
    PL_PESEL(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    
    // Singapore specific
    SG_NRIC_FIN(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    SG_UEN(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    
    // Australia specific
    AU_ABN(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    AU_ACN(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    AU_TFN(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    AU_MEDICARE(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    
    // India specific
    IN_PAN(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    IN_AADHAAR(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    IN_VEHICLE_REGISTRATION(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    IN_VOTER(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    IN_PASSPORT(ContentPiiDetection.PersonallyIdentifiableInformationType.ID_CARD),
    
    // Finland specific
    FI_PERSONAL_IDENTITY_CODE(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    
    // Korea specific
    KR_RRN(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    
    // Thailand specific
    TH_TNIN(ContentPiiDetection.PersonallyIdentifiableInformationType.SSN),
    
    // Technical tokens
    API_KEY(ContentPiiDetection.PersonallyIdentifiableInformationType.API_KEY),
    JWT_TOKEN(ContentPiiDetection.PersonallyIdentifiableInformationType.TOKEN),
    GITHUB_TOKEN(ContentPiiDetection.PersonallyIdentifiableInformationType.TOKEN),
    AWS_KEY(ContentPiiDetection.PersonallyIdentifiableInformationType.API_KEY),
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
