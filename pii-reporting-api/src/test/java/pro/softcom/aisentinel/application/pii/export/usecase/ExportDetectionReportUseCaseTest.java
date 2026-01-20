package pro.softcom.aisentinel.application.pii.export.usecase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.export.DetectionReportMapper;
import pro.softcom.aisentinel.application.pii.export.dto.DetectionReportEntry;
import pro.softcom.aisentinel.application.pii.export.exception.ExportException;
import pro.softcom.aisentinel.application.pii.export.port.out.ReadExportContextPort;
import pro.softcom.aisentinel.application.pii.export.port.out.ReadScanEventsPort;
import pro.softcom.aisentinel.application.pii.export.port.out.WriteDetectionReportPort;
import pro.softcom.aisentinel.domain.pii.export.ExportContext;
import pro.softcom.aisentinel.domain.pii.export.SourceType;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Export detection report use case tests")
class ExportDetectionReportUseCaseTest {

    @Mock
    private ReadScanEventsPort readScanEventsPort;

    @Mock
    private WriteDetectionReportPort writeDetectionReportPort;

    @Mock
    private DetectionReportMapper detectionReportMapper;

    @Mock
    private ReadExportContextPort readExportContextPort;

    @InjectMocks
    private ExportDetectionReportUseCase useCase;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  "})
    @DisplayName("Should_ThrowException_When_ScanIdIsBlankOrNull")
    void Should_ThrowException_When_ScanIdIsBlankOrNull(String scanId) {
        // Given (scanId provided by parameter)

        // When & Then
        assertThatThrownBy(() -> useCase.export(scanId, SourceType.CONFLUENCE, "TEST"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scanId is required");

        verifyNoInteractions(readExportContextPort, readScanEventsPort, writeDetectionReportPort, detectionReportMapper);
    }

    @Test
    @DisplayName("Should_ThrowException_When_SourceTypeIsNull")
    void Should_ThrowException_When_SourceTypeIsNull() {
        // Given
        String scanId = "scan-123";

        // When & Then
        assertThatThrownBy(() -> useCase.export(scanId, null, "TEST"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceType is required");

        verifyNoInteractions(readExportContextPort, readScanEventsPort, writeDetectionReportPort, detectionReportMapper);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  "})
    @DisplayName("Should_ThrowException_When_SourceIdentifierIsBlankOrNull")
    void Should_ThrowException_When_SourceIdentifierIsBlankOrNull(String sourceIdentifier) {
        // Given (sourceIdentifier provided by parameter)

        // When & Then
        assertThatThrownBy(() -> useCase.export("scan-123", SourceType.CONFLUENCE, sourceIdentifier))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceIdentifier is required");

        verifyNoInteractions(readExportContextPort, readScanEventsPort, writeDetectionReportPort, detectionReportMapper);
    }

    @Test
    @DisplayName("Should_ExportSuccessfully_When_ValidParametersProvided")
    void Should_ExportSuccessfully_When_ValidParametersProvided() throws IOException {
        // Given
        ExportContext exportContext = createExportContext();
        WriteDetectionReportPort.ReportSession reportSession = mock(WriteDetectionReportPort.ReportSession.class);
        ConfluenceContentScanResult confluenceContentScanResult = createScanResult();
        DetectionReportEntry entry = createDetectionReportEntry();

        when(readExportContextPort.findContext(SourceType.CONFLUENCE, "TEST")).thenReturn(exportContext);
        when(writeDetectionReportPort.openReportSession("scan-123", exportContext)).thenReturn(reportSession);
        when(readScanEventsPort.streamByScanIdAndSpaceKey("scan-123", "TEST")).thenReturn(Stream.of(
            confluenceContentScanResult));
        when(detectionReportMapper.toDetectionReportEntries(
            confluenceContentScanResult)).thenReturn(List.of(entry));

        // When
        useCase.export("scan-123", SourceType.CONFLUENCE, "TEST");

        // Then
        verify(readExportContextPort).findContext(SourceType.CONFLUENCE, "TEST");
        verify(reportSession).startReport();
        verify(reportSession).writeReportEntry(entry);
        verify(reportSession).finishReport();
        verify(reportSession).close();
    }

    @Test
    @DisplayName("Should_HandleMultipleEntries_When_MultipleScanResults")
    void Should_HandleMultipleEntries_When_MultipleScanResults() throws IOException {
        // Given
        ExportContext exportContext = createExportContext();
        WriteDetectionReportPort.ReportSession reportSession = mock(WriteDetectionReportPort.ReportSession.class);
        
        ConfluenceContentScanResult confluenceContentScanResult1 = createScanResult();
        ConfluenceContentScanResult confluenceContentScanResult2 = createScanResult();
        DetectionReportEntry entry1 = createDetectionReportEntry();
        DetectionReportEntry entry2 = createDetectionReportEntry();

        when(readExportContextPort.findContext(SourceType.CONFLUENCE, "TEST")).thenReturn(exportContext);
        when(writeDetectionReportPort.openReportSession("scan-123", exportContext)).thenReturn(reportSession);
        when(readScanEventsPort.streamByScanIdAndSpaceKey("scan-123", "TEST"))
                .thenReturn(Stream.of(confluenceContentScanResult1, confluenceContentScanResult2));
        when(detectionReportMapper.toDetectionReportEntries(
            confluenceContentScanResult1)).thenReturn(List.of(entry1));
        when(detectionReportMapper.toDetectionReportEntries(
            confluenceContentScanResult2)).thenReturn(List.of(entry2));

        // When
        useCase.export("scan-123", SourceType.CONFLUENCE, "TEST");

        // Then
        verify(reportSession, times(2)).writeReportEntry(any(DetectionReportEntry.class));
        verify(reportSession).close();
    }

    @Test
    @DisplayName("Should_HandleEmptyResults_When_NoDetectionsFound")
    void Should_HandleEmptyResults_When_NoDetectionsFound() throws IOException {
        // Given
        ExportContext exportContext = createExportContext();
        WriteDetectionReportPort.ReportSession reportSession = mock(WriteDetectionReportPort.ReportSession.class);

        when(readExportContextPort.findContext(SourceType.CONFLUENCE, "TEST")).thenReturn(exportContext);
        when(writeDetectionReportPort.openReportSession("scan-123", exportContext)).thenReturn(reportSession);
        when(readScanEventsPort.streamByScanIdAndSpaceKey("scan-123", "TEST")).thenReturn(Stream.empty());

        // When
        useCase.export("scan-123", SourceType.CONFLUENCE, "TEST");

        // Then
        verify(reportSession).startReport();
        verify(reportSession).finishReport();
        verify(reportSession).close();
        verify(reportSession, never()).writeReportEntry(any());
    }

    @Test
    @DisplayName("Should_ThrowExportException_When_IOExceptionOccursDuringExport")
    void Should_ThrowExportException_When_IOExceptionOccursDuringExport() throws IOException {
        // Given
        ExportContext exportContext = createExportContext();
        WriteDetectionReportPort.ReportSession reportSession = mock(WriteDetectionReportPort.ReportSession.class);

        when(readExportContextPort.findContext(SourceType.CONFLUENCE, "TEST")).thenReturn(exportContext);
        when(writeDetectionReportPort.openReportSession("scan-123", exportContext)).thenReturn(reportSession);
        doThrow(new IOException("Write failed")).when(reportSession).startReport();

        // When & Then
        assertThatThrownBy(() -> useCase.export("scan-123", SourceType.CONFLUENCE, "TEST"))
                .isInstanceOf(ExportException.class)
                .hasMessage("Failed to export findings")
                .hasCauseInstanceOf(IOException.class);

        verify(reportSession).close();
    }

    @Test
    @DisplayName("Should_CloseSession_When_ExceptionOccurs")
    void Should_CloseSession_When_ExceptionOccurs() throws IOException {
        // Given
        ExportContext exportContext = createExportContext();
        WriteDetectionReportPort.ReportSession reportSession = mock(WriteDetectionReportPort.ReportSession.class);
        ConfluenceContentScanResult confluenceContentScanResult = createScanResult();
        DetectionReportEntry entry = createDetectionReportEntry();

        when(readExportContextPort.findContext(SourceType.CONFLUENCE, "TEST")).thenReturn(exportContext);
        when(writeDetectionReportPort.openReportSession("scan-123", exportContext)).thenReturn(reportSession);
        when(readScanEventsPort.streamByScanIdAndSpaceKey("scan-123", "TEST")).thenReturn(Stream.of(
            confluenceContentScanResult));
        when(detectionReportMapper.toDetectionReportEntries(
            confluenceContentScanResult)).thenReturn(List.of(entry));
        doThrow(new IOException("Write entry failed")).when(reportSession).writeReportEntry(entry);

        // When & Then
        assertThatThrownBy(() -> useCase.export("scan-123", SourceType.CONFLUENCE, "TEST"))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IOException.class);

        verify(reportSession).close();
    }

    @Test
    @DisplayName("Should_HandleMultipleEntriesPerResult_When_ScanResultHasMultipleDetections")
    void Should_HandleMultipleEntriesPerResult_When_ScanResultHasMultipleDetections() throws IOException {
        // Given
        ExportContext exportContext = createExportContext();
        WriteDetectionReportPort.ReportSession reportSession = mock(WriteDetectionReportPort.ReportSession.class);
        ConfluenceContentScanResult confluenceContentScanResult = createScanResult();
        DetectionReportEntry entry1 = createDetectionReportEntry();
        DetectionReportEntry entry2 = createDetectionReportEntry();
        DetectionReportEntry entry3 = createDetectionReportEntry();

        when(readExportContextPort.findContext(SourceType.CONFLUENCE, "TEST")).thenReturn(exportContext);
        when(writeDetectionReportPort.openReportSession("scan-123", exportContext)).thenReturn(reportSession);
        when(readScanEventsPort.streamByScanIdAndSpaceKey("scan-123", "TEST")).thenReturn(Stream.of(
            confluenceContentScanResult));
        when(detectionReportMapper.toDetectionReportEntries(confluenceContentScanResult))
                .thenReturn(List.of(entry1, entry2, entry3));

        // When
        useCase.export("scan-123", SourceType.CONFLUENCE, "TEST");

        // Then
        verify(reportSession, times(3)).writeReportEntry(any(DetectionReportEntry.class));
        verify(reportSession).close();
    }

    private ExportContext createExportContext() {
        return ExportContext.builder()
                .reportName("Test Space")
                .reportIdentifier("TEST")
                .sourceUrl("https://example.com/space/TEST")
                .contacts(List.of())
                .additionalMetadata(Map.of())
                .build();
    }

    private ConfluenceContentScanResult createScanResult() {
        return ConfluenceContentScanResult.builder()
                .scanId("scan-123")
                .spaceKey("TEST")
                .emittedAt("2024-01-15T10:00:00Z")
                .pageTitle("Test Page")
                .pageUrl("https://example.com/page")
                .attachmentName("test.pdf")
                .attachmentUrl("https://example.com/attachment")
                .detectedPIIList(List.of(
                    DetectedPersonallyIdentifiableInformation.builder()
                                .piiType("EMAIL")
                                .piiTypeLabel("Email")
                                .maskedContext("test@example.com")
                                .confidence(0.95)
                                .build()
                ))
                .build();
    }

    private DetectionReportEntry createDetectionReportEntry() {
        return DetectionReportEntry.builder()
                .scanId("scan-123")
                .spaceKey("TEST")
                .emittedAt("2024-01-15T10:00:00Z")
                .pageTitle("Test Page")
                .pageUrl("https://example.com/page")
                .attachmentName("test.pdf")
                .attachmentUrl("https://example.com/attachment")
                .type("EMAIL")
                .typeLabel("Email")
                .maskedContext("test@example.com")
                .confidenceScore(0.95)
                .build();
    }
}
