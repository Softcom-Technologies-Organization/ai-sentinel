package pro.softcom.aisentinel.domain.pii.scan;

import lombok.Getter;

/**
 * Enumeration of event types emitted during a Confluence scan.
 * These types represent the different stages of a scan lifecycle.
 */
@Getter
public enum ScanEventType {
    MULTI_START("multiStart"),
    START("start"),
    PAGE_START("pageStart"),
    ITEM("item"),
    ATTACHMENT_ITEM("attachmentItem"),
    PAGE_COMPLETE("pageComplete"),
    ERROR("scanError"),
    COMPLETE("complete"),
    MULTI_COMPLETE("multiComplete"),
    KEEPALIVE("keepalive");

    private final String value;

    ScanEventType(String value) {
        this.value = value;
    }

    public static ScanEventType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ScanEventType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}
