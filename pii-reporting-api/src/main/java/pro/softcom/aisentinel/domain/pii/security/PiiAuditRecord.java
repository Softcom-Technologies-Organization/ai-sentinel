package pro.softcom.aisentinel.domain.pii.security;

import pro.softcom.aisentinel.domain.pii.reporting.AccessPurpose;

import java.time.Instant;

/**
 * Domain model representing a PII access audit record.
 * Used for GDPR/nLPD compliance tracking.
 *
 * <p>Business Rule: Each PII access must be logged with purpose and retention period.</p>
 */
public record PiiAuditRecord(
        String scanId,
        String spaceKey,
        String pageId,
        String pageTitle,
        Instant accessedAt,
        Instant retentionUntil,
        AccessPurpose purpose,
        int piiEntitiesCount
) {
    public PiiAuditRecord {
        if (scanId == null || scanId.isBlank()) {
            throw new IllegalArgumentException("scanId is required");
        }
        if (accessedAt == null) {
            throw new IllegalArgumentException("accessedAt is required");
        }
        if (retentionUntil == null) {
            throw new IllegalArgumentException("retentionUntil is required");
        }
        if (purpose == null) {
            throw new IllegalArgumentException("purpose is required");
        }
        if (piiEntitiesCount < 0) {
            throw new IllegalArgumentException("piiEntitiesCount must be non-negative");
        }
    }
}
