package pro.softcom.aisentinel.infrastructure.pii.export.adapter.in;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.export.usecase.ExportDetectionReportUseCase;
import pro.softcom.aisentinel.domain.pii.export.SourceType;
import pro.softcom.aisentinel.domain.pii.scan.SpaceScanCompleted;

@ExtendWith(MockitoExtension.class)
@DisplayName("Space scan completed listener tests")
class SpaceScanCompletedListenerTest {

    @Mock
    private ExportDetectionReportUseCase exportDetectionReportUseCase;

    @InjectMocks
    private SpaceScanCompletedListener listener;

    @Test
    @DisplayName("Should_CallExportUseCase_When_ValidEventReceived")
    void Should_CallExportUseCase_When_ValidEventReceived() {
        // Given
        SpaceScanCompleted event = new SpaceScanCompleted("scan-123", "TEST-KEY");

        // When
        listener.onSpaceScanCompleted(event);

        // Then
        verify(exportDetectionReportUseCase).export("scan-123", SourceType.CONFLUENCE, "TEST-KEY");
    }

    @Test
    @DisplayName("Should_NotThrowException_When_NullEventReceived")
    void Should_NotThrowException_When_NullEventReceived() {
        // Given (null event)

        // When
        listener.onSpaceScanCompleted(null);

        // Then
        verifyNoInteractions(exportDetectionReportUseCase);
    }

    @Test
    @DisplayName("Should_CatchException_When_ExportFails")
    void Should_CatchException_When_ExportFails() {
        // Given
        SpaceScanCompleted event = new SpaceScanCompleted("scan-456", "FAIL-KEY");
        doThrow(new RuntimeException("Export failed"))
                .when(exportDetectionReportUseCase)
                .export("scan-456", SourceType.CONFLUENCE, "FAIL-KEY");

        // When
        listener.onSpaceScanCompleted(event);

        // Then
        verify(exportDetectionReportUseCase).export("scan-456", SourceType.CONFLUENCE, "FAIL-KEY");
    }

    @Test
    @DisplayName("Should_HandleMultipleEvents_When_CalledSequentially")
    void Should_HandleMultipleEvents_When_CalledSequentially() {
        // Given
        SpaceScanCompleted event1 = new SpaceScanCompleted("scan-1", "KEY-1");
        SpaceScanCompleted event2 = new SpaceScanCompleted("scan-2", "KEY-2");
        SpaceScanCompleted event3 = new SpaceScanCompleted("scan-3", "KEY-3");

        // When
        listener.onSpaceScanCompleted(event1);
        listener.onSpaceScanCompleted(event2);
        listener.onSpaceScanCompleted(event3);

        // Then
        verify(exportDetectionReportUseCase).export("scan-1", SourceType.CONFLUENCE, "KEY-1");
        verify(exportDetectionReportUseCase).export("scan-2", SourceType.CONFLUENCE, "KEY-2");
        verify(exportDetectionReportUseCase).export("scan-3", SourceType.CONFLUENCE, "KEY-3");
    }

    @Test
    @DisplayName("Should_ContinueProcessing_When_OneEventFails")
    void Should_ContinueProcessing_When_OneEventFails() {
        // Given
        SpaceScanCompleted event1 = new SpaceScanCompleted("scan-1", "KEY-1");
        SpaceScanCompleted event2 = new SpaceScanCompleted("scan-2", "KEY-2");

        doThrow(new RuntimeException("Export failed for scan-1"))
                .when(exportDetectionReportUseCase)
                .export("scan-1", SourceType.CONFLUENCE, "KEY-1");

        // When
        listener.onSpaceScanCompleted(event1);
        listener.onSpaceScanCompleted(event2);

        // Then
        verify(exportDetectionReportUseCase).export("scan-1", SourceType.CONFLUENCE, "KEY-1");
        verify(exportDetectionReportUseCase).export("scan-2", SourceType.CONFLUENCE, "KEY-2");
    }
}
