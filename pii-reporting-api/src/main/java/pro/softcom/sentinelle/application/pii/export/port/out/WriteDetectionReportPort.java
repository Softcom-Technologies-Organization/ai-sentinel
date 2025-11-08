package pro.softcom.sentinelle.application.pii.export.port.out;

import pro.softcom.sentinelle.application.pii.export.dto.DetectionReportEntry;
import pro.softcom.sentinelle.domain.pii.export.ExportContext;

import java.io.IOException;

/**
 * Port for writing detection reports in various formats.
 * This abstraction allows the application layer to work with any export format
 * without coupling to specific implementations.
 */
public interface WriteDetectionReportPort {
    /**
     * Opens a new report session for writing detection entries.
     *
     * @param scanId the unique identifier of the scan
     * @param exportContext the context containing metadata for the report
     * @return a report session for writing entries
     * @throws IOException if an I/O error occurs
     */
    ReportSession openReportSession(String scanId, ExportContext exportContext) throws IOException;

    interface ReportSession extends AutoCloseable {
        void startReport() throws IOException;

        void finishReport() throws IOException;

        void writeReportEntry(DetectionReportEntry finding) throws IOException;

        @Override
        void close() throws IOException;
    }
}
