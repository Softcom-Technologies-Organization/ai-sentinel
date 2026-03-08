package pro.softcom.aisentinel.infrastructure.pii.export.adapter.out;

import pro.softcom.aisentinel.application.pii.export.port.out.ReadExportContextPort;
import pro.softcom.aisentinel.domain.pii.export.ExportContext;
import pro.softcom.aisentinel.domain.pii.export.SourceType;

import java.util.List;
import java.util.Map;

/**
 * Provides export context for database scan sources.
 * Since database sources don't have an external project/space to look up,
 * the context is built directly from the source identifier (table name).
 */
public class DatabaseExportContextAdapter implements ReadExportContextPort {

    @Override
    public ExportContext findContext(SourceType sourceType, String sourceIdentifier) {
        return ExportContext.builder()
                .reportName("Database: " + sourceIdentifier)
                .reportIdentifier(sourceIdentifier)
                .sourceUrl(null)
                .contacts(List.of())
                .additionalMetadata(Map.of("sourceType", "database"))
                .build();
    }
}
