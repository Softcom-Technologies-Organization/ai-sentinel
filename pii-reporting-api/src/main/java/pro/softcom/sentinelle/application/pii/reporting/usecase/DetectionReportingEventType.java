package pro.softcom.sentinelle.application.pii.reporting.usecase;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enumerates known Server-Sent Event types emitted during a Confluence scan.
 */
@RequiredArgsConstructor
@Getter
public enum DetectionReportingEventType {
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

    private final String label;
}
