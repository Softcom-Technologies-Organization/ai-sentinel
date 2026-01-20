package pro.softcom.aisentinel.domain.pii.export;

import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Represents the business context for exporting detection reports.
 * This is a pure domain concept, independent of any specific platform (Confluence, JIRA, etc.).
 * It contains all necessary metadata to generate an export report.
 */
@Builder
public record ExportContext(
        String reportName,
        String reportIdentifier,
        String sourceUrl,
        List<DataSourceContact> contacts,
        Map<String, String> additionalMetadata
) {
    public ExportContext {
        if (reportName == null || reportName.isBlank()) {
            throw new IllegalArgumentException("Report name cannot be empty");
        }
        if (reportIdentifier == null || reportIdentifier.isBlank()) {
            throw new IllegalArgumentException("Report identifier cannot be empty");
        }
        if (contacts == null) {
            contacts = List.of();
        }
        if (additionalMetadata == null) {
            additionalMetadata = Map.of();
        }
    }
}
