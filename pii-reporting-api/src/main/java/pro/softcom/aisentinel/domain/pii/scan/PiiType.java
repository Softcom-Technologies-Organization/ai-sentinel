package pro.softcom.aisentinel.domain.pii.scan;

/**
 * Enumeration of PII (Personally Identifiable Information) types emitted by the gRPC server.
 * Aligned with server taxonomy including types from ML detector, Presidio, and Regex detectors.
 *
 * What: Each enum constant carries its business-level ContentAnalysis.DataType mapping.
 * How to use: Clients must parse detector labels strictly with {@code PiiType.valueOf(label)}
 * (case-insensitive via upper-casing) and retrieve {@link #dataType()}.
 * No aliasing or fallback mapping is provided here to keep tests and production aligned.
 */
public enum PiiType {
    // Original types from ML detector
    ACCOUNTNUM(ContentPiiDetection.DataType.BANK_ACCOUNT),
    BUILDINGNUM(ContentPiiDetection.DataType.BUILDINGNUM),
    CITY(ContentPiiDetection.DataType.CITY),
    CREDITCARDNUMBER(ContentPiiDetection.DataType.CREDIT_CARD),
    DATEOFBIRTH(ContentPiiDetection.DataType.PERSON), // closest business category
    DRIVERLICENSENUM(ContentPiiDetection.DataType.ID_CARD),
    EMAIL(ContentPiiDetection.DataType.EMAIL),
    GIVENNAME(ContentPiiDetection.DataType.NAME),
    IDCARDNUM(ContentPiiDetection.DataType.ID_CARD),
    PASSWORD(ContentPiiDetection.DataType.PASSWORD),
    SOCIALNUM(ContentPiiDetection.DataType.SSN),
    STREET(ContentPiiDetection.DataType.STREET),
    SURNAME(ContentPiiDetection.DataType.SURNAME),
    TAXNUM(ContentPiiDetection.DataType.SSN), // treated as sensitive identifier
    TELEPHONENUM(ContentPiiDetection.DataType.PHONE),
    USERNAME(ContentPiiDetection.DataType.USERNAME),
    ZIPCODE(ContentPiiDetection.DataType.ZIPCODE),
    
    // Additional types from Presidio and Regex detectors
    PHONE(ContentPiiDetection.DataType.PHONE),
    URL(ContentPiiDetection.DataType.URL),
    CREDIT_CARD(ContentPiiDetection.DataType.CREDIT_CARD),
    IBAN(ContentPiiDetection.DataType.BANK_ACCOUNT),
    CRYPTO_WALLET(ContentPiiDetection.DataType.BANK_ACCOUNT),
    SSN(ContentPiiDetection.DataType.SSN),
    NHS_NUMBER(ContentPiiDetection.DataType.SSN),
    NRIC(ContentPiiDetection.DataType.ID_CARD),
    ABN(ContentPiiDetection.DataType.ID_CARD),
    ACN(ContentPiiDetection.DataType.ID_CARD),
    TFN(ContentPiiDetection.DataType.SSN),
    MEDICARE(ContentPiiDetection.DataType.SSN),
    IP_ADDRESS(ContentPiiDetection.DataType.IP_ADDRESS),
    MAC_ADDRESS(ContentPiiDetection.DataType.IP_ADDRESS),
    PERSON_NAME(ContentPiiDetection.DataType.NAME),
    LOCATION(ContentPiiDetection.DataType.LOCATION),
    DATE(ContentPiiDetection.DataType.PERSON),
    AGE(ContentPiiDetection.DataType.PERSON),
    MEDICAL_LICENSE(ContentPiiDetection.DataType.ID_CARD),
    PASSPORT(ContentPiiDetection.DataType.ID_CARD),
    DRIVER_LICENSE(ContentPiiDetection.DataType.ID_CARD),
    ITIN(ContentPiiDetection.DataType.SSN),
    NRP(ContentPiiDetection.DataType.PERSON),
    
    // USA specific
    US_BANK_NUMBER(ContentPiiDetection.DataType.BANK_ACCOUNT),
    US_DRIVER_LICENSE(ContentPiiDetection.DataType.ID_CARD),
    US_ITIN(ContentPiiDetection.DataType.SSN),
    US_PASSPORT(ContentPiiDetection.DataType.ID_CARD),
    US_SSN(ContentPiiDetection.DataType.SSN),
    
    // UK specific
    UK_NHS(ContentPiiDetection.DataType.SSN),
    UK_NINO(ContentPiiDetection.DataType.SSN),
    
    // Spain specific
    ES_NIF(ContentPiiDetection.DataType.SSN),
    ES_NIE(ContentPiiDetection.DataType.ID_CARD),
    
    // Italy specific
    IT_FISCAL_CODE(ContentPiiDetection.DataType.SSN),
    IT_DRIVER_LICENSE(ContentPiiDetection.DataType.ID_CARD),
    IT_VAT_CODE(ContentPiiDetection.DataType.SSN),
    IT_PASSPORT(ContentPiiDetection.DataType.ID_CARD),
    IT_IDENTITY_CARD(ContentPiiDetection.DataType.ID_CARD),
    
    // Poland specific
    PL_PESEL(ContentPiiDetection.DataType.SSN),
    
    // Singapore specific
    SG_NRIC_FIN(ContentPiiDetection.DataType.ID_CARD),
    SG_UEN(ContentPiiDetection.DataType.ID_CARD),
    
    // Australia specific
    AU_ABN(ContentPiiDetection.DataType.ID_CARD),
    AU_ACN(ContentPiiDetection.DataType.ID_CARD),
    AU_TFN(ContentPiiDetection.DataType.SSN),
    AU_MEDICARE(ContentPiiDetection.DataType.SSN),
    
    // India specific
    IN_PAN(ContentPiiDetection.DataType.SSN),
    IN_AADHAAR(ContentPiiDetection.DataType.SSN),
    IN_VEHICLE_REGISTRATION(ContentPiiDetection.DataType.ID_CARD),
    IN_VOTER(ContentPiiDetection.DataType.ID_CARD),
    IN_PASSPORT(ContentPiiDetection.DataType.ID_CARD),
    
    // Finland specific
    FI_PERSONAL_IDENTITY_CODE(ContentPiiDetection.DataType.SSN),
    
    // Korea specific
    KR_RRN(ContentPiiDetection.DataType.SSN),
    
    // Thailand specific
    TH_TNIN(ContentPiiDetection.DataType.SSN),
    
    // Technical tokens
    API_KEY(ContentPiiDetection.DataType.API_KEY),
    JWT_TOKEN(ContentPiiDetection.DataType.TOKEN),
    GITHUB_TOKEN(ContentPiiDetection.DataType.TOKEN),
    AWS_KEY(ContentPiiDetection.DataType.API_KEY),
    UNKNOWN(ContentPiiDetection.DataType.UNKNOWN);

    private final ContentPiiDetection.DataType dataType;

    PiiType(ContentPiiDetection.DataType dataType) {
        this.dataType = dataType;
    }

    public ContentPiiDetection.DataType dataType() {
        return dataType;
    }



    public String toLowerCase() {
        return name().toLowerCase();
    }
}
