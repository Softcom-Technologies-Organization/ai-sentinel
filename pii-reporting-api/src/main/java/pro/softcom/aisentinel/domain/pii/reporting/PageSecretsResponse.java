package pro.softcom.aisentinel.domain.pii.reporting;

import java.util.List;

/**
 * Domain model representing the response containing revealed PII secrets for a page.
 * 
 * <p>Business Rule: Contains all revealed secrets for a specific Confluence page
 * within a scan context.</p>
 */
public record PageSecretsResponse(
        String scanId,
        String pageId,
        String pageTitle,
        List<RevealedSecret> secrets
) {
    public PageSecretsResponse {
        if (scanId == null || scanId.isBlank()) {
            throw new IllegalArgumentException("scanId must not be blank");
        }
        if (pageId == null || pageId.isBlank()) {
            throw new IllegalArgumentException("pageId must not be blank");
        }
        if (secrets == null) {
            throw new IllegalArgumentException("secrets must not be null");
        }
    }
}
