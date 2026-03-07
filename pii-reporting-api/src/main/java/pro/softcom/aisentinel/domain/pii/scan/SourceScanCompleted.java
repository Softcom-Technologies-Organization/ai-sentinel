package pro.softcom.aisentinel.domain.pii.scan;

import pro.softcom.aisentinel.domain.pii.export.SourceType;

import java.util.Objects;

/**
 * Domain event signaling the completion of a source scan (Confluence space, Jira project, etc.).
 * This event encapsulates the business information needed to identify a completed scan
 * and the type of source that was scanned.
 */
public record SourceScanCompleted(String scanId, String sourceKey, SourceType sourceType) {

    public SourceScanCompleted {
        if (scanId == null || scanId.isBlank()) {
            throw new IllegalArgumentException("scanId cannot be empty");
        }
        if (sourceKey == null || sourceKey.isBlank()) {
            throw new IllegalArgumentException("sourceKey cannot be empty");
        }
        Objects.requireNonNull(sourceType, "sourceType cannot be null");
    }
}
