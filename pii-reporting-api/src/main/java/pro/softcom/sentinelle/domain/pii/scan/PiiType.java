package pro.softcom.sentinelle.domain.pii.scan;

/**
 * Enumeration of PII (Personally Identifiable Information) types emitted by the gRPC server.
 * Aligned exactly with server taxonomy (17 types) to avoid ambiguity.
 *
 * What: Each enum constant carries its business-level ContentAnalysis.DataType mapping.
 * How to use: Clients must parse detector labels strictly with {@code PiiType.valueOf(label)}
 * (case-insensitive via upper-casing) and retrieve {@link #dataType()}.
 * No aliasing or fallback mapping is provided here to keep tests and production aligned.
 */
public enum PiiType {
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
