package pro.softcom.aisentinel.domain.pii.export;

/**
 * Represents the type of source system where PII detections are exported from.
 * This enumeration allows the system to handle different platforms in a type-safe manner.
 */
public enum SourceType {
    /**
     * Confluence platform - collaborative workspace for teams
     */
    CONFLUENCE("CONFLUENCE");

    private final String value;

    SourceType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse a string value to SourceType enum.
     *
     * @param value the string value to parse
     * @return the corresponding SourceType
     * @throws IllegalArgumentException if the value doesn't match any SourceType
     */
    public static SourceType fromValue(String value) {
        for (SourceType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown source type: " + value);
    }
}
