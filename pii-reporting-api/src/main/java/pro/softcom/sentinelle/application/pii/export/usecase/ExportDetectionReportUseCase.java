package pro.softcom.sentinelle.application.pii.export.usecase;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import pro.softcom.sentinelle.application.pii.export.DetectionReportMapper;
import pro.softcom.sentinelle.application.pii.export.dto.DetectionReportEntry;
import pro.softcom.sentinelle.application.pii.export.exception.ExportException;
import pro.softcom.sentinelle.application.pii.export.port.in.ExportDetectionReportPort;
import pro.softcom.sentinelle.application.pii.export.port.out.ReadExportContextPort;
import pro.softcom.sentinelle.application.pii.export.port.out.ReadScanEventsPort;
import pro.softcom.sentinelle.application.pii.export.port.out.WriteDetectionReportPort;
import pro.softcom.sentinelle.domain.pii.export.ExportContext;
import pro.softcom.sentinelle.domain.pii.export.SourceType;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;

/**
 * Use case for exporting detection reports.
 * This use case orchestrates the export process by retrieving the export context,
 * reading scan events, and writing them to the report in the desired format.
 */
@RequiredArgsConstructor
@Slf4j
public class ExportDetectionReportUseCase implements ExportDetectionReportPort {

    private final ReadScanEventsPort readScanEventsPort;
    private final WriteDetectionReportPort writeDetectionReportPort;
    private final DetectionReportMapper detectionReportMapper;
    private final ReadExportContextPort readExportContextPort;

    /**
     * Exports detection report for a given scan and source.
     *
     * @param scanId           the unique identifier of the scan
     * @param sourceType       the type of source
     * @param sourceIdentifier the unique identifier of the source (e.g., space key)
     */
    public void export(String scanId, SourceType sourceType, String sourceIdentifier) {
        log.info("Starting export for scanId={} sourceType={} sourceIdentifier={}",
                scanId, sourceType, sourceIdentifier);

        validateExportParameters(scanId, sourceType, sourceIdentifier);

        ExportContext exportContext = readExportContextPort.findContext(sourceType, sourceIdentifier);
        exportDetectionReport(scanId, sourceIdentifier, exportContext);
    }

    private void validateExportParameters(String scanId, SourceType sourceType, String sourceIdentifier) {
        if (StringUtils.isBlank(scanId)) {
            throw new IllegalArgumentException("scanId is required");
        }
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType is required");
        }
        if (StringUtils.isBlank(sourceIdentifier)) {
            throw new IllegalArgumentException("sourceIdentifier is required");
        }
    }

    private void exportDetectionReport(String scanId, String sourceIdentifier, ExportContext exportContext) {
        try (var reportSession = writeDetectionReportPort.openReportSession(scanId, exportContext)) {
            reportSession.startReport();
            writeReportEntries(reportSession, scanId, sourceIdentifier);
            reportSession.finishReport();
            log.info("Export completed for scanId={} sourceIdentifier={}", scanId, sourceIdentifier);
        } catch (IOException e) {
            throw new ExportException("Failed to export findings", e);
        }
    }

    private void writeReportEntries(
            WriteDetectionReportPort.ReportSession reportSession,
            String scanId,
            String sourceIdentifier
    ) {
        try {
            var scanResults = readScanEventsPort.streamByScanIdAndSpaceKey(scanId, sourceIdentifier);

            for (ScanResult scanResult : scanResults.toList()) {
                writeEntriesForScanResult(reportSession, scanResult);
            }
        } catch (IOException e) {
            throw new ExportException("Failed to write report entries", e);
        }
    }

    private void writeEntriesForScanResult(
            WriteDetectionReportPort.ReportSession reportSession,
            ScanResult scanResult
    ) throws IOException {
        var entries = detectionReportMapper.toDetectionReportEntries(scanResult);

        for (DetectionReportEntry entry : entries) {
            reportSession.writeReportEntry(entry);
        }
    }
}
