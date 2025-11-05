package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * DTO adapter for JSON serialization of scan event types.
 * Maps domain types to the JSON format expected by clients.
 */
public enum ScanEventType {
    MULTI_START("multiStart"),
    START("start"),
    PAGE_START("pageStart"),
    ITEM("item"),
    ATTACHMENT_ITEM("attachmentItem"),
    PAGE_COMPLETE("pageComplete"),
    ERROR("error"),
    COMPLETE("complete"),
    MULTI_COMPLETE("multiComplete"),
    KEEPALIVE("keepalive");

    private final String json;

    ScanEventType(String json) {
        this.json = json;
    }

    @JsonValue
    public String toJson() {
        return json;
    }

    @JsonCreator
    public static ScanEventType from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ScanEventType t : values()) {
            if (t.json.equalsIgnoreCase(value)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Converts a domain event type to an infrastructure DTO.
     */
    public static ScanEventType fromDomain(pro.softcom.sentinelle.domain.pii.scan.ScanEventType domainType) {
        if (domainType == null) {
            return null;
        }
        return from(domainType.getValue());
    }

    /**
     * Converts this DTO to a domain event type.
     */
    public pro.softcom.sentinelle.domain.pii.scan.ScanEventType toDomain() {
        return pro.softcom.sentinelle.domain.pii.scan.ScanEventType.fromValue(this.json);
    }
}
