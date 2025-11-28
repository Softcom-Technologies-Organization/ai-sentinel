package pro.softcom.aisentinel.application.pii.reporting.usecase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
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
 * Business Rule: BR-SCAN-001 - When pausing a scan, only RUNNING checkpoints
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
    void Should_OnlyPauseRunningCheckpoints_When_ScanHasMixedStatuses() {
        // Given: A scan with 3 spaces in different states
        String scanId = "scan-mixed-123";
        
        ScanCheckpoint runningSpace = ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey("SPACE-RUNNING")
            .scanStatus(ScanStatus.RUNNING)
            .progressPercentage(50.0)
            .build();

        ScanCheckpoint completedSpace = ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey("SPACE-COMPLETED")
            .scanStatus(ScanStatus.COMPLETED)
            .progressPercentage(100.0)
            .build();

        ScanCheckpoint anotherRunningSpace = ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey("SPACE-RUNNING-2")
            .scanStatus(ScanStatus.RUNNING)
            .progressPercentage(75.0)
            .build();

        when(scanCheckpointRepository.findByScan(scanId))
            .thenReturn(List.of(runningSpace, completedSpace, anotherRunningSpace));

        when(scanTaskManager.pauseScan(scanId)).thenReturn(true);

        // When: Pausing the scan
        pauseScanUseCase.pauseScan(scanId);

        // Then: Only RUNNING checkpoints should be updated to PAUSED
        verify(scanCheckpointRepository, times(2)).save(
            argThat(checkpoint -> 
                checkpoint.scanStatus() == ScanStatus.PAUSED &&
                (checkpoint.spaceKey().equals("SPACE-RUNNING") || 
                 checkpoint.spaceKey().equals("SPACE-RUNNING-2"))
            )
        );

        // And: COMPLETED checkpoint should NOT be saved (transition blocked)
        verify(scanCheckpointRepository, never()).save(
            argThat(checkpoint -> 
                checkpoint.spaceKey().equals("SPACE-COMPLETED")
            )
        );
    }

    @Test
    void Should_SkipCompletedCheckpoints_When_PausingScan() {
        // Given: A scan where all spaces are COMPLETED
        String scanId = "scan-all-completed";
        
        ScanCheckpoint completedSpace1 = ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey("SPACE-1")
            .scanStatus(ScanStatus.COMPLETED)
            .progressPercentage(100.0)
            .build();

        ScanCheckpoint completedSpace2 = ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey("SPACE-2")
            .scanStatus(ScanStatus.COMPLETED)
            .progressPercentage(100.0)
            .build();

        when(scanCheckpointRepository.findByScan(scanId))
            .thenReturn(List.of(completedSpace1, completedSpace2));

        when(scanTaskManager.pauseScan(scanId)).thenReturn(true);

        // When: Attempting to pause the scan
        pauseScanUseCase.pauseScan(scanId);

        // Then: No checkpoints should be updated (all transitions blocked)
        verify(scanCheckpointRepository, never()).save(any(ScanCheckpoint.class));
    }

    @Test
    void Should_PauseAllRunningCheckpoints_When_NoCompletedSpaces() {
        // Given: A scan with only RUNNING spaces
        String scanId = "scan-all-running";
        
        ScanCheckpoint runningSpace1 = ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey("SPACE-A")
            .scanStatus(ScanStatus.RUNNING)
            .progressPercentage(30.0)
            .build();

        ScanCheckpoint runningSpace2 = ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey("SPACE-B")
            .scanStatus(ScanStatus.RUNNING)
            .progressPercentage(60.0)
            .build();

        when(scanCheckpointRepository.findByScan(scanId))
            .thenReturn(List.of(runningSpace1, runningSpace2));

        when(scanTaskManager.pauseScan(scanId)).thenReturn(true);

        // When: Pausing the scan
        pauseScanUseCase.pauseScan(scanId);

        // Then: All RUNNING checkpoints should be updated to PAUSED
        verify(scanCheckpointRepository, times(2)).save(
            argThat(checkpoint -> checkpoint.scanStatus() == ScanStatus.PAUSED)
        );
    }
}
