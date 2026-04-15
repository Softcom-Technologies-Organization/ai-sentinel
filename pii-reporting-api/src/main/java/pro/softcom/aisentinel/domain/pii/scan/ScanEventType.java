package pro.softcom.aisentinel.domain.pii.scan;

import lombok.Getter;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /**
     * Pre-computed index for case-insensitive O(1) lookup by domain value.
     * Populated once at class initialization; avoids allocating a new
     * {@code values()} array on every {@link #fromValue(String)} call.
     */
    private static final Map<String, ScanEventType> BY_VALUE = Arrays.stream(values())
        .collect(Collectors.toUnmodifiableMap(
            t -> t.value.toLowerCase(Locale.ROOT),
            Function.identity()));

    private final String value;

    ScanEventType(String value) {
        this.value = value;
    }

    public static ScanEventType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return BY_VALUE.get(value.toLowerCase(Locale.ROOT));
    }
}
