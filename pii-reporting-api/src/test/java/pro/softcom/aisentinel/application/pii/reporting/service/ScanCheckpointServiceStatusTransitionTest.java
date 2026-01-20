package pro.softcom.aisentinel.application.pii.reporting.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that ScanCheckpointService validates status transitions correctly
 * to prevent race conditions like COMPLETED → PAUSED.
 * Business Rule: BR-SCAN-001 - Final states (COMPLETED, FAILED) are immutable.
 */
@ExtendWith(MockitoExtension.class)
class ScanCheckpointServiceStatusTransitionTest {

    @Mock
    private ScanCheckpointRepository scanCheckpointRepository;

    @InjectMocks
    private ScanCheckpointService scanCheckpointService;

    @Test
    void Should_BlockTransition_When_CheckpointIsCompleted() {
        // Given: An existing COMPLETED checkpoint
        ScanCheckpoint existingCheckpoint = ScanCheckpoint.builder()
            .scanId("scan-123")
            .spaceKey("TEST")
            .lastProcessedPageId("page-456")
            .scanStatus(ScanStatus.COMPLETED)
            .progressPercentage(100.0)
            .build();

        when(scanCheckpointRepository.findByScanAndSpace("scan-123", "TEST"))
            .thenReturn(Optional.of(existingCheckpoint));

        // And: A new scan event trying to update to RUNNING (invalid transition)
        ConfluenceContentScanResult scanResult = ConfluenceContentScanResult.builder()
            .scanId("scan-123")
            .spaceKey("TEST")
            .eventType("pageComplete")
            .pageId("page-789")
            .build();

        // When: Attempting to persist checkpoint with RUNNING status
        scanCheckpointService.persistCheckpoint(scanResult);

        // Then: Repository save should NOT be called (transition blocked)
        verify(scanCheckpointRepository, never()).save(any(ScanCheckpoint.class));
    }

    @Test
    void Should_BlockTransition_When_CheckpointIsFailed() {
        // Given: An existing FAILED checkpoint
        ScanCheckpoint existingCheckpoint = ScanCheckpoint.builder()
            .scanId("scan-456")
            .spaceKey("PROD")
            .lastProcessedPageId("page-100")
            .scanStatus(ScanStatus.FAILED)
            .progressPercentage(45.0)
            .build();

        when(scanCheckpointRepository.findByScanAndSpace("scan-456", "PROD"))
            .thenReturn(Optional.of(existingCheckpoint));

        // And: A scan event trying to complete the space (invalid transition)
        ConfluenceContentScanResult scanResult = ConfluenceContentScanResult.builder()
            .scanId("scan-456")
            .spaceKey("PROD")
            .eventType("complete")
            .build();

        // When: Attempting to persist checkpoint with COMPLETED status
        scanCheckpointService.persistCheckpoint(scanResult);

        // Then: Repository save should NOT be called (transition blocked)
        verify(scanCheckpointRepository, never()).save(any(ScanCheckpoint.class));
    }

    @Test
    void Should_AllowTransition_When_CheckpointIsRunning() {
        // Given: An existing RUNNING checkpoint
        ScanCheckpoint existingCheckpoint = ScanCheckpoint.builder()
            .scanId("scan-789")
            .spaceKey("DEV")
            .lastProcessedPageId("page-200")
            .scanStatus(ScanStatus.RUNNING)
            .progressPercentage(75.0)
            .build();

        when(scanCheckpointRepository.findByScanAndSpace("scan-789", "DEV"))
            .thenReturn(Optional.of(existingCheckpoint));

        // And: A scan event completing the space (valid transition: RUNNING → COMPLETED)
        ConfluenceContentScanResult scanResult = ConfluenceContentScanResult.builder()
            .scanId("scan-789")
            .spaceKey("DEV")
            .eventType("complete")
            .analysisProgressPercentage(100.0)
            .build();

        // When: Attempting to persist checkpoint with COMPLETED status
        scanCheckpointService.persistCheckpoint(scanResult);

        // Then: Repository save SHOULD be called (transition allowed)
        verify(scanCheckpointRepository).save(any(ScanCheckpoint.class));
    }

    @Test
    void Should_AllowFirstCheckpoint_When_NoExistingCheckpoint() {
        // Given: No existing checkpoint
        when(scanCheckpointRepository.findByScanAndSpace("scan-new", "SPACE"))
            .thenReturn(Optional.empty());

        // And: A scan event starting a new scan
        ConfluenceContentScanResult scanResult = ConfluenceContentScanResult.builder()
            .scanId("scan-new")
            .spaceKey("SPACE")
            .eventType("pageComplete")
            .pageId("page-1")
            .build();

        // When: Persisting first checkpoint
        scanCheckpointService.persistCheckpoint(scanResult);

        // Then: Repository save SHOULD be called (first checkpoint allowed)
        verify(scanCheckpointRepository).save(any(ScanCheckpoint.class));
    }

    @Test
    void Should_AllowIdempotentTransition_When_StatusUnchanged() {
        // Given: An existing RUNNING checkpoint
        ScanCheckpoint existingCheckpoint = ScanCheckpoint.builder()
            .scanId("scan-same")
            .spaceKey("SAME")
            .lastProcessedPageId("page-50")
            .scanStatus(ScanStatus.RUNNING)
            .progressPercentage(50.0)
            .build();

        when(scanCheckpointRepository.findByScanAndSpace("scan-same", "SAME"))
            .thenReturn(Optional.of(existingCheckpoint));

        // And: A scan event with same status (idempotent: RUNNING → RUNNING)
        ConfluenceContentScanResult scanResult = ConfluenceContentScanResult.builder()
            .scanId("scan-same")
            .spaceKey("SAME")
            .eventType("pageComplete")
            .pageId("page-51")
            .analysisProgressPercentage(52.0)
            .build();

        // When: Persisting checkpoint with same status
        scanCheckpointService.persistCheckpoint(scanResult);

        // Then: Repository save SHOULD be called (idempotent transition allowed)
        verify(scanCheckpointRepository).save(any(ScanCheckpoint.class));
    }

    @Test
    void Should_BlockCompletedToPausedTransition_When_RaceCondition() {
        // Given: A checkpoint that just transitioned to COMPLETED
        ScanCheckpoint completedCheckpoint = ScanCheckpoint.builder()
            .scanId("scan-race")
            .spaceKey("RACE")
            .lastProcessedPageId("page-final")
            .scanStatus(ScanStatus.COMPLETED)
            .progressPercentage(100.0)
            .build();

        when(scanCheckpointRepository.findByScanAndSpace("scan-race", "RACE"))
            .thenReturn(Optional.of(completedCheckpoint));

        // And: A pause event arrives late (race condition scenario)
        // In reality, PauseScanUseCase would try to transition COMPLETED → PAUSED
        // This test simulates if a RUNNING event tries to overwrite COMPLETED
        ConfluenceContentScanResult scanResult = ConfluenceContentScanResult.builder()
            .scanId("scan-race")
            .spaceKey("RACE")
            .eventType("pageComplete")
            .pageId("page-late")
            .build();

        // When: Attempting to persist checkpoint (would create RUNNING status)
        scanCheckpointService.persistCheckpoint(scanResult);

        // Then: Repository save should NOT be called (final state is immutable)
        verify(scanCheckpointRepository, never()).save(any(ScanCheckpoint.class));
    }
}
