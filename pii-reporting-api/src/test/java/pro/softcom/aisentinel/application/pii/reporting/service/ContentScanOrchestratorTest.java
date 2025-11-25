package pro.softcom.aisentinel.application.pii.reporting.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.reporting.ScanSeverityCountService;
import pro.softcom.aisentinel.application.pii.reporting.SeverityCalculationService;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanEventStore;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;
import pro.softcom.aisentinel.domain.pii.reporting.ScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.SeverityCounts;

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
                scanSeverityCountService
        );
    }

    @Nested
    @DisplayName("Severity Counting Integration")
    class SeverityCountingIntegration {

        @Test
        @DisplayName("Should_CalculateAndPersistCounts_When_EventHasPiiDetections")
        void Should_CalculateAndPersistCounts_When_EventHasPiiDetections() {
            // Given
            String scanId = "scan-123";
            String spaceKey = "PROJ";
            List<DetectedPersonallyIdentifiableInformation> detectedEntities = List.of(
                    new DetectedPersonallyIdentifiableInformation(10, 21, "ssn", "SSN", 0.95, "123-45-6789", "context", "masked"),
                    new DetectedPersonallyIdentifiableInformation(30, 47, "email", "Email", 0.98, "test@example.com", "context", "masked"),
                    new DetectedPersonallyIdentifiableInformation(50, 68, "credit_card", "Credit Card", 0.99, "4111-1111-1111-1111", "context", "masked")
            );

            ScanResult event = ScanResult.builder()
                    .scanId(scanId)
                    .spaceKey(spaceKey)
                    .eventType("ITEM")
                    .pageId("page-1")
                    .pageTitle("Page Title")
                    .detectedPersonallyIdentifiableInformationList(detectedEntities)
                    .analysisProgressPercentage(50.0)
                    .build();

            SeverityCounts calculatedCounts = new SeverityCounts(2, 1, 0);
            when(severityCalculationService.aggregateCounts(detectedEntities))
                    .thenReturn(calculatedCounts);

            // When
            orchestrator.persistEventAndCheckpoint(event);

            // Then
            verify(scanCheckpointService).persistCheckpoint(event);
            verify(severityCalculationService).aggregateCounts(detectedEntities);
            verify(scanSeverityCountService).incrementCounts(scanId, spaceKey, calculatedCounts);
            verify(scanEventStore).append(event);
        }

        @Test
        @DisplayName("Should_NotCalculateCounts_When_EventHasEmptyDetections")
        void Should_NotCalculateCounts_When_EventHasEmptyDetections() {
            // Given
            String scanId = "scan-123";
            String spaceKey = "PROJ";

            ScanResult event = ScanResult.builder()
                    .scanId(scanId)
                    .spaceKey(spaceKey)
                    .eventType("ITEM")
                    .pageId("page-1")
                    .pageTitle("Page Title")
                    .detectedPersonallyIdentifiableInformationList(Collections.emptyList())
                    .analysisProgressPercentage(50.0)
                    .build();

            // When
            orchestrator.persistEventAndCheckpoint(event);

            // Then
            verify(scanCheckpointService).persistCheckpoint(event);
            verifyNoInteractions(severityCalculationService);
            verifyNoInteractions(scanSeverityCountService);
            verify(scanEventStore).append(event);
        }

        @Test
        @DisplayName("Should_NotCalculateCounts_When_DetectedEntitiesIsNull")
        void Should_NotCalculateCounts_When_DetectedEntitiesIsNull() {
            // Given
            String scanId = "scan-123";
            String spaceKey = "PROJ";

            ScanResult event = ScanResult.builder()
                    .scanId(scanId)
                    .spaceKey(spaceKey)
                    .eventType("START")
                    .analysisProgressPercentage(0.0)
                    .build();

            // When
            orchestrator.persistEventAndCheckpoint(event);

            // Then
            verify(scanCheckpointService).persistCheckpoint(event);
            verifyNoInteractions(severityCalculationService);
            verifyNoInteractions(scanSeverityCountService);
            verify(scanEventStore).append(event);
        }

        @Test
        @DisplayName("Should_ExecuteInCorrectOrder_When_PersistingWithDetections")
        void Should_ExecuteInCorrectOrder_When_PersistingWithDetections() {
            // Given
            String scanId = "scan-123";
            String spaceKey = "PROJ";
            List<DetectedPersonallyIdentifiableInformation> detectedEntities = List.of(
                    new DetectedPersonallyIdentifiableInformation(10, 27, "email", "Email", 0.98, "test@example.com", "context", "masked")
            );

            ScanResult event = ScanResult.builder()
                    .scanId(scanId)
                    .spaceKey(spaceKey)
                    .eventType("ITEM")
                    .pageId("page-1")
                    .pageTitle("Page Title")
                    .detectedPersonallyIdentifiableInformationList(detectedEntities)
                    .analysisProgressPercentage(50.0)
                    .build();

            SeverityCounts calculatedCounts = new SeverityCounts(0, 1, 0);
            when(severityCalculationService.aggregateCounts(detectedEntities))
                    .thenReturn(calculatedCounts);

            // When
            orchestrator.persistEventAndCheckpoint(event);

            // Then - Verify order of execution
            InOrder inOrder = inOrder(
                    scanCheckpointService,
                    severityCalculationService,
                    scanSeverityCountService,
                    scanEventStore
            );
            inOrder.verify(scanCheckpointService).persistCheckpoint(event);
            inOrder.verify(severityCalculationService).aggregateCounts(detectedEntities);
            inOrder.verify(scanSeverityCountService).incrementCounts(scanId, spaceKey, calculatedCounts);
            inOrder.verify(scanEventStore).append(event);
        }

        @Test
        @DisplayName("Should_PassCorrectParameters_When_IncrementingCounts")
        void Should_PassCorrectParameters_When_IncrementingCounts() {
            // Given
            String scanId = "scan-456";
            String spaceKey = "TEST";
            List<DetectedPersonallyIdentifiableInformation> detectedEntities = List.of(
                    new DetectedPersonallyIdentifiableInformation(0, 11, "ssn", "SSN", 0.99, "123-45-6789", "context", "masked")
            );

            ScanResult event = ScanResult.builder()
                    .scanId(scanId)
                    .spaceKey(spaceKey)
                    .eventType("ITEM")
                    .pageId("page-2")
                    .pageTitle("Test Page")
                    .detectedPersonallyIdentifiableInformationList(detectedEntities)
                    .analysisProgressPercentage(75.0)
                    .build();

            SeverityCounts expectedCounts = new SeverityCounts(1, 0, 0);
            when(severityCalculationService.aggregateCounts(detectedEntities))
                    .thenReturn(expectedCounts);

            // When
            orchestrator.persistEventAndCheckpoint(event);

            // Then - Capture and verify arguments
            ArgumentCaptor<String> scanIdCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> spaceKeyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<SeverityCounts> countsCaptor = ArgumentCaptor.forClass(SeverityCounts.class);

            verify(scanSeverityCountService).incrementCounts(
                    scanIdCaptor.capture(),
                    spaceKeyCaptor.capture(),
                    countsCaptor.capture()
            );

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(scanIdCaptor.getValue())
                    .as("Scan ID should match")
                    .isEqualTo(scanId);
            softly.assertThat(spaceKeyCaptor.getValue())
                    .as("Space key should match")
                    .isEqualTo(spaceKey);
            softly.assertThat(countsCaptor.getValue())
                    .as("Severity counts should match")
                    .isEqualTo(expectedCounts);
            softly.assertAll();
        }

        @Test
        @DisplayName("Should_HandleMultipleDetections_When_PersistingEvent")
        void Should_HandleMultipleDetections_When_PersistingEvent() {
            // Given
            String scanId = "scan-789";
            String spaceKey = "MULTI";
            List<DetectedPersonallyIdentifiableInformation> detectedEntities = List.of(
                    new DetectedPersonallyIdentifiableInformation(0, 11, "ssn", "SSN", 0.99, "123-45-6789", "context", "masked"),
                    new DetectedPersonallyIdentifiableInformation(20, 31, "ssn", "SSN", 0.98, "987-65-4321", "context", "masked"),
                    new DetectedPersonallyIdentifiableInformation(40, 58, "credit_card", "Credit Card", 0.97, "4111-1111-1111-1111", "context", "masked"),
                    new DetectedPersonallyIdentifiableInformation(60, 77, "email", "Email", 0.96, "test1@example.com", "context", "masked"),
                    new DetectedPersonallyIdentifiableInformation(80, 97, "email", "Email", 0.95, "test2@example.com", "context", "masked"),
                    new DetectedPersonallyIdentifiableInformation(100, 114, "phone", "Phone", 0.94, "(555) 123-4567", "context", "masked")
            );

            ScanResult event = ScanResult.builder()
                    .scanId(scanId)
                    .spaceKey(spaceKey)
                    .eventType("ITEM")
                    .pageId("page-3")
                    .pageTitle("Multi PII Page")
                    .detectedPersonallyIdentifiableInformationList(detectedEntities)
                    .analysisProgressPercentage(90.0)
                    .build();

            SeverityCounts calculatedCounts = new SeverityCounts(3, 3, 0);
            when(severityCalculationService.aggregateCounts(detectedEntities))
                    .thenReturn(calculatedCounts);

            // When
            orchestrator.persistEventAndCheckpoint(event);

            // Then
            verify(severityCalculationService).aggregateCounts(detectedEntities);
            verify(scanSeverityCountService).incrementCounts(scanId, spaceKey, calculatedCounts);
        }

        @Test
        @DisplayName("Should_NotPublishEvent_When_EventTypeIsNotComplete")
        void Should_NotPublishEvent_When_EventTypeIsNotComplete() {
            // Given
            String scanId = "scan-pub";
            String spaceKey = "PUB";
            List<DetectedPersonallyIdentifiableInformation> detectedEntities = List.of(
                    new DetectedPersonallyIdentifiableInformation(0, 17, "email", "Email", 0.98, "test@example.com", "context", "masked")
            );

            ScanResult event = ScanResult.builder()
                    .scanId(scanId)
                    .spaceKey(spaceKey)
                    .eventType("ITEM")
                    .pageId("page-1")
                    .pageTitle("Test")
                    .detectedPersonallyIdentifiableInformationList(detectedEntities)
                    .analysisProgressPercentage(50.0)
                    .build();

            SeverityCounts calculatedCounts = new SeverityCounts(0, 1, 0);
            when(severityCalculationService.aggregateCounts(detectedEntities))
                    .thenReturn(calculatedCounts);

            // When
            orchestrator.persistEventAndCheckpoint(event);

            // Then
            verifyNoInteractions(scanEventDispatcher);
        }

        @Test
        @DisplayName("Should_PublishEvent_When_EventTypeIsComplete")
        void Should_PublishEvent_When_EventTypeIsComplete() {
            // Given
            String scanId = "scan-complete";
            String spaceKey = "COMP";

            ScanResult event = ScanResult.builder()
                    .scanId(scanId)
                    .spaceKey(spaceKey)
                    .eventType("complete")  // Must match ScanEventType.COMPLETE.getValue()
                    .analysisProgressPercentage(100.0)
                    .build();

            // When
            orchestrator.persistEventAndCheckpoint(event);

            // Then
            verify(scanEventDispatcher).publishAfterCommit(scanId, spaceKey);
        }
    }

    @Nested
    @DisplayName("Event Store Null Handling")
    class EventStoreNullHandling {

        @Test
        @DisplayName("Should_NotAppendOrPublish_When_EventStoreIsNull")
        void Should_NotAppendOrPublish_When_EventStoreIsNull() {
            // Given
            orchestrator = new ContentScanOrchestrator(
                    scanEventFactory,
                    scanProgressCalculator,
                    scanCheckpointService,
                    null,  // null event store
                    scanEventDispatcher,
                    severityCalculationService,
                    scanSeverityCountService
            );

            String scanId = "scan-null";
            String spaceKey = "NULL";
            List<DetectedPersonallyIdentifiableInformation> detectedEntities = List.of(
                    new DetectedPersonallyIdentifiableInformation(0, 17, "email", "Email", 0.98, "test@example.com", "context", "masked")
            );

            ScanResult event = ScanResult.builder()
                    .scanId(scanId)
                    .spaceKey(spaceKey)
                    .eventType("complete")  // Must match ScanEventType.COMPLETE.getValue()
                    .detectedPersonallyIdentifiableInformationList(detectedEntities)
                    .analysisProgressPercentage(100.0)
                    .build();

            SeverityCounts calculatedCounts = new SeverityCounts(0, 1, 0);
            when(severityCalculationService.aggregateCounts(detectedEntities))
                    .thenReturn(calculatedCounts);

            // When
            orchestrator.persistEventAndCheckpoint(event);

            // Then
            verify(scanCheckpointService).persistCheckpoint(event);
            verify(severityCalculationService).aggregateCounts(detectedEntities);
            verify(scanSeverityCountService).incrementCounts(scanId, spaceKey, calculatedCounts);
            verifyNoInteractions(scanEventDispatcher);
        }

        @Test
        @DisplayName("Should_StillCalculateSeverity_When_EventStoreIsNull")
        void Should_StillCalculateSeverity_When_EventStoreIsNull() {
            // Given
            orchestrator = new ContentScanOrchestrator(
                    scanEventFactory,
                    scanProgressCalculator,
                    scanCheckpointService,
                    null,  // null event store
                    scanEventDispatcher,
                    severityCalculationService,
                    scanSeverityCountService
            );

            String scanId = "scan-severity";
            String spaceKey = "SEV";
            List<DetectedPersonallyIdentifiableInformation> detectedEntities = List.of(
                    new DetectedPersonallyIdentifiableInformation(0, 11, "ssn", "SSN", 0.99, "123-45-6789", "context", "masked")
            );

            ScanResult event = ScanResult.builder()
                    .scanId(scanId)
                    .spaceKey(spaceKey)
                    .eventType("ITEM")
                    .pageId("page-1")
                    .pageTitle("Test")
                    .detectedPersonallyIdentifiableInformationList(detectedEntities)
                    .analysisProgressPercentage(50.0)
                    .build();

            SeverityCounts calculatedCounts = new SeverityCounts(1, 0, 0);
            when(severityCalculationService.aggregateCounts(detectedEntities))
                    .thenReturn(calculatedCounts);

            // When
            orchestrator.persistEventAndCheckpoint(event);

            // Then
            verify(severityCalculationService).aggregateCounts(detectedEntities);
            verify(scanSeverityCountService).incrementCounts(scanId, spaceKey, calculatedCounts);
        }
    }
}
