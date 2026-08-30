package pro.softcom.aisentinel.application.pii.reporting.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.reporting.ScanPiiTypeCountService;
import pro.softcom.aisentinel.application.pii.reporting.ScanSeverityCountService;
import pro.softcom.aisentinel.application.pii.reporting.SeverityCalculationService;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanEventStore;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;
import pro.softcom.aisentinel.domain.pii.reporting.SeverityCounts;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorSource;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentScanOrchestrator - Severity Integration Tests")
class ContentScanOrchestratorTest {

    @Mock
    private ScanEventFactory scanEventFactory;

    @Mock
    private ScanProgressCalculator scanProgressCalculator;

    @Mock
    private ScanCheckpointService scanCheckpointService;

    @Mock
    private ScanEventStore scanEventStore;

    @Mock
    private ScanEventDispatcher scanEventDispatcher;

    @Mock
    private SeverityCalculationService severityCalculationService;

    @Mock
    private ScanSeverityCountService scanSeverityCountService;

    @Mock
    private ScanPiiTypeCountService scanPiiTypeCountService;

    private ContentScanOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ContentScanOrchestrator(
                scanEventFactory,
                scanProgressCalculator,
                scanCheckpointService,
                scanEventStore,
                scanEventDispatcher,
                severityCalculationService,
                scanSeverityCountService,
                scanPiiTypeCountService
        );
    }

    @Nested
    @DisplayName("Synchronous Checkpoint Persistence")
    class SynchronousCheckpointPersistence {

        @Test
        @DisplayName("Should_PersistCheckpoint_When_CalledSynchronously")
        void Should_PersistCheckpoint_When_CalledSynchronously() {
            // Given
            String scanId = "scan-sync";
            String spaceKey = "SYNC";
            ConfluenceContentScanResult event = ConfluenceContentScanResult.builder()
                    .scanId(scanId)
                    .spaceKey(spaceKey)
                    .eventType("pageComplete")
                    .pageId("page-1")
                    .analysisProgressPercentage(50.0)
                    .build();

            // When
            orchestrator.persistCheckpointSynchronously(event);

            // Then
            verify(scanCheckpointService).persistCheckpoint(event);
            verifyNoInteractions(severityCalculationService);
            verifyNoInteractions(scanSeverityCountService);
            verifyNoInteractions(scanEventStore);
        }

        @Test
        @DisplayName("Should_NotThrowException_When_CheckpointPersistenceFails")
        void Should_NotThrowException_When_CheckpointPersistenceFails() {
            // Given
            String scanId = "scan-fail";
            String spaceKey = "FAIL";
            ConfluenceContentScanResult event = ConfluenceContentScanResult.builder()
                    .scanId(scanId)
                    .spaceKey(spaceKey)
                    .eventType("pageComplete")
                    .build();

            doThrow(new RuntimeException("DB error"))
                    .when(scanCheckpointService).persistCheckpoint(event);

            // When & Then - should not throw
            org.assertj.core.api.Assertions.assertThatCode(() -> 
                    orchestrator.persistCheckpointSynchronously(event)
            ).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Async Operations Persistence")
    class AsyncOperationsPersistence {

        @Test
        @DisplayName("Should_PersistSeverityCountsAndEventStore_When_EventHasDetections")
        void Should_PersistSeverityCountsAndEventStore_When_EventHasDetections() {
            // Given
            String scanId = "scan-async";
            String spaceKey = "ASYNC";
            List<DetectedPersonallyIdentifiableInformation> detectedEntities = List.of(
                    new DetectedPersonallyIdentifiableInformation(10, 27, "email", "Email", 0.98, "test@example.com", "context", "masked", DetectorSource.UNKNOWN_SOURCE, null)
            );

            ConfluenceContentScanResult event = ConfluenceContentScanResult.builder()
                    .scanId(scanId)
                    .spaceKey(spaceKey)
                    .eventType("item")
                    .pageId("page-1")
                    .detectedPIIs(detectedEntities)
                    .analysisProgressPercentage(50.0)
                    .build();

            SeverityCounts calculatedCounts = new SeverityCounts(0, 1, 0);
            when(severityCalculationService.aggregateCounts(detectedEntities))
                    .thenReturn(calculatedCounts);

            // When
            orchestrator.persistEventAsyncOperations(event);

            // Then
            verifyNoInteractions(scanCheckpointService); // Checkpoint NOT persisted here
            verify(severityCalculationService).aggregateCounts(detectedEntities);
            verify(scanSeverityCountService).incrementCounts(scanId, spaceKey, calculatedCounts);
            verify(scanEventStore).append(event);
        }

        @Test
        @DisplayName("Should_PersistPiiTypeCounts_When_EventHasTypeCounts")
        void Should_PersistPiiTypeCounts_When_EventHasTypeCounts() {
            // Given
            String scanId = "scan-type";
            String spaceKey = "TYPE";
            Map<String, Integer> typeCounts = Map.of("EMAIL", 2, "IBAN_CODE", 1);

            ConfluenceContentScanResult event = ConfluenceContentScanResult.builder()
                    .scanId(scanId)
                    .spaceKey(spaceKey)
                    .eventType("item")
                    .pageId("page-1")
                    .detectedPiiCountByType(typeCounts)
                    .analysisProgressPercentage(50.0)
                    .build();

            // When
            orchestrator.persistEventAsyncOperations(event);

            // Then
            verify(scanPiiTypeCountService).incrementCounts(scanId, spaceKey, typeCounts);
        }

        @Test
        @DisplayName("Should_NotPersistPiiTypeCounts_When_TypeCountsEmpty")
        void Should_NotPersistPiiTypeCounts_When_TypeCountsEmpty() {
            // Given
            ConfluenceContentScanResult event = ConfluenceContentScanResult.builder()
                    .scanId("scan-empty")
                    .spaceKey("EMPTY")
                    .eventType("item")
                    .pageId("page-1")
                    .detectedPiiCountByType(Map.of())
                    .analysisProgressPercentage(50.0)
                    .build();

            // When
            orchestrator.persistEventAsyncOperations(event);

            // Then
            verifyNoInteractions(scanPiiTypeCountService);
        }

        @Test
        @DisplayName("Should_NotPersistCheckpoint_When_CallingAsyncOperations")
        void Should_NotPersistCheckpoint_When_CallingAsyncOperations() {
            // Given
            String scanId = "scan-no-cp";
            String spaceKey = "NOCP";
            ConfluenceContentScanResult event = ConfluenceContentScanResult.builder()
                    .scanId(scanId)
                    .spaceKey(spaceKey)
                    .eventType("item")
                    .pageId("page-1")
                    .analysisProgressPercentage(50.0)
                    .build();

            // When
            orchestrator.persistEventAsyncOperations(event);

            // Then
            verifyNoInteractions(scanCheckpointService);
            verify(scanEventStore).append(event);
        }
    }
}