package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    ERROR("scanError"),
    COMPLETE("complete"),
    MULTI_COMPLETE("multiComplete"),
    KEEPALIVE("keepalive");

    /**
     * Pre-computed index for case-insensitive O(1) lookup by JSON value.
     * Populated once at class initialization; avoids allocating a new
     * {@code values()} array on every {@link #from(String)} call in the
     * SSE event hot path.
     */
    private static final Map<String, ScanEventType> BY_JSON = Arrays.stream(values())
        .collect(Collectors.toUnmodifiableMap(
            t -> t.json.toLowerCase(Locale.ROOT),
            Function.identity()));

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
        return BY_JSON.get(value.toLowerCase(Locale.ROOT));
    }

    /**
     * Converts a domain event type to an infrastructure DTO.
     */
    public static ScanEventType fromDomain(
        pro.softcom.aisentinel.domain.pii.scan.ScanEventType domainType) {
        if (domainType == null) {
            return null;
        }
        return from(domainType.getValue());
    }

    /**
     * Converts this DTO to a domain event type.
     */
    public pro.softcom.aisentinel.domain.pii.scan.ScanEventType toDomain() {
        return pro.softcom.aisentinel.domain.pii.scan.ScanEventType.fromValue(this.json);
    }
}
