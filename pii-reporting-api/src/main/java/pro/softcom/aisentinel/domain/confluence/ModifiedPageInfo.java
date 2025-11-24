package pro.softcom.aisentinel.domain.confluence;

import java.time.Instant;

/**
 * Represents a Confluence page modified within a specific timeframe.
 * Business purpose: Used by the space update detection feature to identify
 * which pages have changed since the last scan, enabling targeted re-scanning.
 *
 * @param pageId unique identifier of the page in Confluence
 * @param title human-readable title of the page for UI display
 * @param lastModified timestamp of the most recent modification to the page
 */
public record ModifiedPageInfo(
    String pageId,
    String title,
    Instant lastModified
) {
}
