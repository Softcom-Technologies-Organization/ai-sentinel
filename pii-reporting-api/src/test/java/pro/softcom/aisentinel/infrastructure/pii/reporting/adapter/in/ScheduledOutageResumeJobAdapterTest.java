package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.reporting.port.in.StreamConfluenceResumeScanPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PersonallyIdentifiableInformationScanExecutionOrchestratorPort;
import pro.softcom.aisentinel.application.pii.reporting.service.OutagePausedScans;
import pro.softcom.aisentinel.application.pii.reporting.usecase.PauseScanUseCase;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduledOutageResumeJobAdapter - automatic retry of outage-paused scans")
class ScheduledOutageResumeJobAdapterTest {

    @Mock
    private StreamConfluenceResumeScanPort streamConfluenceResumeScanPort;

    @Mock
    private PersonallyIdentifiableInformationScanExecutionOrchestratorPort scanExecutionOrchestratorPort;

    @Mock
    private ScanCheckpointRepository scanCheckpointRepository;

    private final OutagePausedScans outagePausedScans = new OutagePausedScans();

    private ScheduledOutageResumeJobAdapter job() {
        return new ScheduledOutageResumeJobAdapter(outagePausedScans, streamConfluenceResumeScanPort,
                                                   scanExecutionOrchestratorPort);
    }

    @Test
    @DisplayName("Should_ResumeScan_When_OutagePausedItAndNothingIsRunning")
    void Should_ResumeScan_When_OutagePausedItAndNothingIsRunning() {
        outagePausedScans.recordOutagePause("scan-1");
        when(scanExecutionOrchestratorPort.hasActiveScan()).thenReturn(false);
        when(streamConfluenceResumeScanPort.resumeAllSpaces("scan-1")).thenReturn(Flux.empty());

        job().retryScansPausedByAnOutage();

        verify(streamConfluenceResumeScanPort).resumeAllSpaces("scan-1");
    }

    @Test
    @DisplayName("Should_WaitForTheNextTick_When_AnotherScanIsAlreadyRunning")
    void Should_WaitForTheNextTick_When_AnotherScanIsAlreadyRunning() {
        outagePausedScans.recordOutagePause("scan-2");
        when(scanExecutionOrchestratorPort.hasActiveScan()).thenReturn(true);

        job().retryScansPausedByAnOutage();

        verify(streamConfluenceResumeScanPort, never()).resumeAllSpaces(anyString());
    }

    @Test
    @DisplayName("Should_NeverResumeScan_When_OperatorPausedItAfterTheOutage")
    void Should_NeverResumeScan_When_OperatorPausedItAfterTheOutage() {
        // The sequence that matters: the network drops and lists the scan for retry,
        // then the operator stops it on purpose. Their decision must be the last word.
        outagePausedScans.recordOutagePause("scan-3");
        new PauseScanUseCase(scanCheckpointRepository, scanExecutionOrchestratorPort, outagePausedScans)
                .pauseScan("scan-3");

        job().retryScansPausedByAnOutage();

        verify(streamConfluenceResumeScanPort, never()).resumeAllSpaces(anyString());
    }

    @Test
    @DisplayName("Should_LeaveScanListedForTheNextTick_When_ItsResumeThrows")
    void Should_LeaveScanListedForTheNextTick_When_ItsResumeThrows() {
        outagePausedScans.recordOutagePause("scan-broken");
        when(scanExecutionOrchestratorPort.hasActiveScan()).thenReturn(false);
        when(streamConfluenceResumeScanPort.resumeAllSpaces("scan-broken"))
                .thenThrow(new IllegalStateException("detector client down"));

        assertThatCode(() -> job().retryScansPausedByAnOutage()).doesNotThrowAnyException();

        assertThat(outagePausedScans.snapshot()).containsExactly("scan-broken");
    }
}
