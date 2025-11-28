package pro.softcom.aisentinel.application.pii.reporting.usecase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanTaskManager;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;

/**
 * Tests for PauseScanUseCase to verify correct behavior when pausing scans.
 * Business Rule: BR-SCAN-001 - When pausing a scan, only the RUNNING checkpoint
 * should transition to PAUSED. COMPLETED checkpoints must remain unchanged.
 */
@ExtendWith(MockitoExtension.class)
class PauseScanUseCaseTest {

    @Mock
    private ScanCheckpointRepository scanCheckpointRepository;

    @Mock
    private ScanTaskManager scanTaskManager;

    @InjectMocks
    private PauseScanUseCase pauseScanUseCase;

    @Test
    void Should_PauseRunningCheckpoint_When_ScanHasOneRunningSpace() {
        // Given: A scan with one RUNNING space
        String scanId = "scan-running-123";
        
        ScanCheckpoint runningSpace = ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey("SPACE-RUNNING")
            .scanStatus(ScanStatus.RUNNING)
            .progressPercentage(50.0)
            .build();

        when(scanCheckpointRepository.findRunningScanCheckpoint(scanId))
            .thenReturn(Optional.of(runningSpace));
        when(scanTaskManager.pauseScan(scanId)).thenReturn(true);

        // When: Pausing the scan
        pauseScanUseCase.pauseScan(scanId);

        // Then: The RUNNING checkpoint should be updated to PAUSED
        verify(scanCheckpointRepository).save(
            argThat(checkpoint -> 
                checkpoint.scanStatus() == ScanStatus.PAUSED &&
                checkpoint.spaceKey().equals("SPACE-RUNNING")
            )
        );
    }

    @Test
    void Should_NotSaveAnything_When_NoRunningCheckpointExists() {
        // Given: A scan where all spaces are COMPLETED (no RUNNING checkpoint)
        String scanId = "scan-all-completed";
        
        when(scanCheckpointRepository.findRunningScanCheckpoint(scanId))
            .thenReturn(Optional.empty());
        when(scanTaskManager.pauseScan(scanId)).thenReturn(true);

        // When: Attempting to pause the scan
        pauseScanUseCase.pauseScan(scanId);

        // Then: No checkpoints should be saved
        verify(scanCheckpointRepository, never()).save(any(ScanCheckpoint.class));
    }

    @Test
    void Should_PreserveCheckpointData_When_PausingRunningSpace() {
        // Given: A scan with one RUNNING space with specific progress
        String scanId = "scan-with-progress";
        
        ScanCheckpoint runningSpace = ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey("SPACE-A")
            .lastProcessedPageId("page-123")
            .lastProcessedAttachmentName("attachment.pdf")
            .scanStatus(ScanStatus.RUNNING)
            .progressPercentage(75.5)
            .build();

        when(scanCheckpointRepository.findRunningScanCheckpoint(scanId))
            .thenReturn(Optional.of(runningSpace));
        when(scanTaskManager.pauseScan(scanId)).thenReturn(true);

        // When: Pausing the scan
        pauseScanUseCase.pauseScan(scanId);

        // Then: The checkpoint should be saved with PAUSED status but preserve all other data
        verify(scanCheckpointRepository).save(
            argThat(checkpoint -> 
                checkpoint.scanStatus() == ScanStatus.PAUSED &&
                checkpoint.spaceKey().equals("SPACE-A") &&
                checkpoint.lastProcessedPageId().equals("page-123") &&
                checkpoint.lastProcessedAttachmentName().equals("attachment.pdf") &&
                checkpoint.progressPercentage() == 75.5
            )
        );
    }

    @Test
    void Should_DoNothing_When_ScanIdIsBlank() {
        // When: Trying to pause with blank scanId
        pauseScanUseCase.pauseScan("");
        pauseScanUseCase.pauseScan(null);
        pauseScanUseCase.pauseScan("   ");

        // Then: No repository calls should be made
        verify(scanCheckpointRepository, never()).findRunningScanCheckpoint(any());
        verify(scanCheckpointRepository, never()).save(any());
        verify(scanTaskManager, never()).pauseScan(any());
    }

    @Test
    void Should_StillUpdateCheckpoint_When_ScanTaskNotFound() {
        // Given: A scan with a RUNNING checkpoint but task already gone
        String scanId = "scan-task-gone";
        
        ScanCheckpoint runningSpace = ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey("SPACE-X")
            .scanStatus(ScanStatus.RUNNING)
            .progressPercentage(30.0)
            .build();

        when(scanCheckpointRepository.findRunningScanCheckpoint(scanId))
            .thenReturn(Optional.of(runningSpace));
        when(scanTaskManager.pauseScan(scanId)).thenReturn(false); // Task not found

        // When: Pausing the scan
        pauseScanUseCase.pauseScan(scanId);

        // Then: The checkpoint should still be updated to PAUSED (persist state)
        verify(scanCheckpointRepository).save(
            argThat(checkpoint -> checkpoint.scanStatus() == ScanStatus.PAUSED)
        );
    }
}
