package pro.softcom.aisentinel.infrastructure.pii.export.adapter.in;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.export.usecase.ExportDetectionReportUseCase;
import pro.softcom.aisentinel.domain.pii.export.SourceType;
import pro.softcom.aisentinel.domain.pii.scan.SourceScanCompleted;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("Source scan completed listener tests")
class SourceScanCompletedListenerTest {

    @Mock
    private ExportDetectionReportUseCase exportDetectionReportUseCase;

    @InjectMocks
    private SourceScanCompletedListener listener;

    @Test
    @DisplayName("Should_CallExportUseCase_When_ValidConfluenceEventReceived")
    void Should_CallExportUseCase_When_ValidConfluenceEventReceived() {
        // Given
        SourceScanCompleted event = new SourceScanCompleted("scan-123", "TEST-KEY", SourceType.CONFLUENCE);

        // When
        listener.onSourceScanCompleted(event);

        // Then
        verify(exportDetectionReportUseCase).export("scan-123", SourceType.CONFLUENCE, "TEST-KEY");
    }

    @Test
    @DisplayName("Should_CallExportUseCase_When_ValidJiraEventReceived")
    void Should_CallExportUseCase_When_ValidJiraEventReceived() {
        // Given
        SourceScanCompleted event = new SourceScanCompleted("scan-jira-1", "PROJ-KEY", SourceType.JIRA);

        // When
        listener.onSourceScanCompleted(event);

        // Then
        verify(exportDetectionReportUseCase).export("scan-jira-1", SourceType.JIRA, "PROJ-KEY");
    }

    @Test
    @DisplayName("Should_CallExportUseCase_When_ValidDatabaseEventReceived")
    void Should_CallExportUseCase_When_ValidDatabaseEventReceived() {
        // Given
        SourceScanCompleted event = new SourceScanCompleted("scan-db-1", "users-table", SourceType.DATABASE);

        // When
        listener.onSourceScanCompleted(event);

        // Then
        verify(exportDetectionReportUseCase).export("scan-db-1", SourceType.DATABASE, "users-table");
    }

    @Test
    @DisplayName("Should_NotThrowException_When_NullEventReceived")
    void Should_NotThrowException_When_NullEventReceived() {
        // Given (null event)

        // When
        listener.onSourceScanCompleted(null);

        // Then
        verifyNoInteractions(exportDetectionReportUseCase);
    }

    @Test
    @DisplayName("Should_CatchException_When_ExportFails")
    void Should_CatchException_When_ExportFails() {
        // Given
        SourceScanCompleted event = new SourceScanCompleted("scan-456", "FAIL-KEY", SourceType.CONFLUENCE);
        doThrow(new RuntimeException("Export failed"))
                .when(exportDetectionReportUseCase)
                .export("scan-456", SourceType.CONFLUENCE, "FAIL-KEY");

        // When
        listener.onSourceScanCompleted(event);

        // Then
        verify(exportDetectionReportUseCase).export("scan-456", SourceType.CONFLUENCE, "FAIL-KEY");
    }

    @Test
    @DisplayName("Should_HandleMultipleEvents_When_CalledSequentially")
    void Should_HandleMultipleEvents_When_CalledSequentially() {
        // Given
        SourceScanCompleted event1 = new SourceScanCompleted("scan-1", "KEY-1", SourceType.CONFLUENCE);
        SourceScanCompleted event2 = new SourceScanCompleted("scan-2", "KEY-2", SourceType.JIRA);
        SourceScanCompleted event3 = new SourceScanCompleted("scan-3", "KEY-3", SourceType.DATABASE);

        // When
        listener.onSourceScanCompleted(event1);
        listener.onSourceScanCompleted(event2);
        listener.onSourceScanCompleted(event3);

        // Then
        verify(exportDetectionReportUseCase).export("scan-1", SourceType.CONFLUENCE, "KEY-1");
        verify(exportDetectionReportUseCase).export("scan-2", SourceType.JIRA, "KEY-2");
        verify(exportDetectionReportUseCase).export("scan-3", SourceType.DATABASE, "KEY-3");
    }

    @Test
    @DisplayName("Should_ContinueProcessing_When_OneEventFails")
    void Should_ContinueProcessing_When_OneEventFails() {
        // Given
        SourceScanCompleted event1 = new SourceScanCompleted("scan-1", "KEY-1", SourceType.CONFLUENCE);
        SourceScanCompleted event2 = new SourceScanCompleted("scan-2", "KEY-2", SourceType.JIRA);

        doThrow(new RuntimeException("Export failed for scan-1"))
                .when(exportDetectionReportUseCase)
                .export("scan-1", SourceType.CONFLUENCE, "KEY-1");

        // When
        listener.onSourceScanCompleted(event1);
        listener.onSourceScanCompleted(event2);

        // Then
        verify(exportDetectionReportUseCase).export("scan-1", SourceType.CONFLUENCE, "KEY-1");
        verify(exportDetectionReportUseCase).export("scan-2", SourceType.JIRA, "KEY-2");
    }
}
