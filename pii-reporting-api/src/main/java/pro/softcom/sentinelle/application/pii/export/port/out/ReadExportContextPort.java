package pro.softcom.sentinelle.application.pii.export.port.out;

import pro.softcom.sentinelle.domain.pii.export.ExportContext;
import pro.softcom.sentinelle.domain.pii.export.SourceType;

/**
 * Port for retrieving export context from various sources.
 * This abstraction allows the application layer to work with any source type
 * (Confluence, JIRA, etc.) without coupling to specific implementations.
 */
public interface ReadExportContextPort {
    /**
     * Retrieves the export context for a given source.
     *
     * @param sourceType the type of source
     * @param sourceIdentifier the unique identifier of the source (e.g., space key, project key)
     * @return the export context containing all necessary metadata
     * @throws pro.softcom.sentinelle.application.pii.export.exception.ExportContextNotFoundException if the context cannot be found
     * @throws pro.softcom.sentinelle.application.pii.export.exception.UnsupportedSourceTypeException if the source type is not supported
     */
    ExportContext findContext(SourceType sourceType, String sourceIdentifier);
}
