package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumerates known Server-Sent Event types emitted during a Confluence scan.
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

    ScanEventType(String json) { this.json = json; }

    @JsonValue
    public String toJson() { return json; }

    @JsonCreator
    public static ScanEventType from(String value) {
        if (value == null || value.isBlank()) return null;
        for (ScanEventType t : values()) {
            if (t.json.equalsIgnoreCase(value)) return t;
        }
        return null;
    }
}
