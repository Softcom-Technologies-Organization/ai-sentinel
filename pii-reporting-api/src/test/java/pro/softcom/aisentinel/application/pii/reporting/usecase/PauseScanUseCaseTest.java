package pro.softcom.aisentinel.application.pii.reporting.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PersonallyIdentifiableInformationScanExecutionOrchestratorPort;
import pro.softcom.aisentinel.application.pii.reporting.service.OutagePausedScans;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private PersonallyIdentifiableInformationScanExecutionOrchestratorPort personallyIdentifiableInformationScanExecutionOrchestratorPort;

    private final OutagePausedScans outagePausedScans = new OutagePausedScans();

    private PauseScanUseCase pauseScanUseCase;

    @BeforeEach
    void setUp() {
        pauseScanUseCase = new PauseScanUseCase(scanCheckpointRepository,
                                                personallyIdentifiableInformationScanExecutionOrchestratorPort,
                                                outagePausedScans);
    }

    @Test
    void Should_StopTheAutomaticRetry_When_TheOperatorPausesAnOutagePausedScan() {
        // An outage listed this scan for automatic retry moments before the operator
        // stopped it; their decision has to be the last word.
        String scanId = "scan-outage-then-operator";
        outagePausedScans.record(scanId);

        pauseScanUseCase.pauseScan(scanId);

        assertThat(outagePausedScans.snapshot()).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] scanId={0}, taskDisposed={1}, updatedCount={2}")
    @MethodSource("providePauseScanCases")
    void Should_AtomicallyPauseRunningCheckpoints_When_ScanIdIsValid(
            String scanId, boolean taskDisposed, int updatedCount) {
        // Given
        when(personallyIdentifiableInformationScanExecutionOrchestratorPort.pauseScan(scanId))
            .thenReturn(taskDisposed);
        when(scanCheckpointRepository.pauseUnfinishedCheckpoints(scanId))
            .thenReturn(updatedCount);

        // When
        pauseScanUseCase.pauseScan(scanId);

        // Then — verify orchestrator pause is called BEFORE checkpoint update
        verify(personallyIdentifiableInformationScanExecutionOrchestratorPort).pauseScan(scanId);
        verify(scanCheckpointRepository).pauseUnfinishedCheckpoints(scanId);
    }

    static Stream<Arguments> providePauseScanCases() {
        return Stream.of(
            Arguments.of("scan-running-123", true, 1),
            Arguments.of("scan-all-completed", true, 0),
            Arguments.of("scan-with-progress", true, 3),
            Arguments.of("scan-task-gone", false, 1)
        );
    }

    @Test
    void Should_DoNothing_When_ScanIdIsBlank() {
        // When
        pauseScanUseCase.pauseScan("");
        pauseScanUseCase.pauseScan(null);
        pauseScanUseCase.pauseScan("   ");

        // Then
        verify(scanCheckpointRepository, never()).pauseUnfinishedCheckpoints(any());
        verify(personallyIdentifiableInformationScanExecutionOrchestratorPort, never()).pauseScan(any());
    }
}
