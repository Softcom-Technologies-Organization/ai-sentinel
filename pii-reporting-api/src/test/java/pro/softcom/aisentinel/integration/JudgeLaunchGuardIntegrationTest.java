package pro.softcom.aisentinel.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.application.pii.judge.port.in.TrackJudgeJobPort;
import pro.softcom.aisentinel.application.pii.judge.port.out.JudgeJobStore;
import pro.softcom.aisentinel.application.pii.judge.service.JudgeJobRunner;
import pro.softcom.aisentinel.application.pii.judge.usecase.JudgeEvaluationUseCase;
import pro.softcom.aisentinel.application.pii.remediation.port.out.RemediationConfigPort;
import pro.softcom.aisentinel.application.pii.remediation.service.EligibleFinding;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver.ResolvedSelection;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PersonallyIdentifiableInformationScanExecutionOrchestratorPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.LastScanMeta;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.PiiJudgeController;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.mapper.JudgeDtoMapper;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.mapper.RemediationJobDtoMapper;
import pro.softcom.aisentinel.infrastructure.shared.adapter.in.GlobalExceptionHandler;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class JudgeLaunchGuardIntegrationTest {

    private static final String JOBS_URL = "/api/v1/pii/judge/jobs";
    private static final String REQUEST = """
            {"scope":{"spaceKeys":["A"]},"model":"judge","applyMode":"PROPOSE"}
            """;

    @Mock private RemediationConfigPort remediationConfigPort;
    @Mock private SelectionResolver selectionResolver;
    @Mock private JudgeJobStore judgeJobStore;
    @Mock private PiiDetectionConfigRepository detectionConfigRepository;
    @Mock private JudgeJobRunner jobRunner;
    @Mock private Executor jobExecutor;
    @Mock private ScanCheckpointRepository checkpointRepository;
    @Mock private ScanResultQuery scanResultQuery;
    @Mock private PersonallyIdentifiableInformationScanExecutionOrchestratorPort scanOrchestrator;
    @Mock private Clock clock;
    @Mock private TrackJudgeJobPort trackJudgeJobPort;
    @InjectMocks private JudgeEvaluationUseCase useCase;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        PiiJudgeController controller = new PiiJudgeController(useCase, trackJudgeJobPort,
                new JudgeDtoMapper(new RemediationJobDtoMapper()));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(remediationConfigPort.isRemediationEnabled()).thenReturn(true);
    }

    @ParameterizedTest
    @EnumSource(value = ScanStatus.class, names = {"RUNNING", "NOT_STARTED", "PAUSED"})
    void Should_ReturnConflictWithoutScheduling_When_ScanUnfinished(ScanStatus scanStatus) throws Exception {
        when(checkpointRepository.findAllLatestCheckpoints()).thenReturn(List.of(checkpoint(scanStatus)));
        if (scanStatus == ScanStatus.PAUSED) {
            when(scanResultQuery.findLatestScan()).thenReturn(Optional.of(new LastScanMeta("scan", Instant.EPOCH, 1)));
        }

        mvc.perform(post(JOBS_URL).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorKey").value("error.judge.scan_in_progress"));

        verifyNoInteractions(selectionResolver, judgeJobStore, jobExecutor, jobRunner);
    }

    @Test
    void Should_ReturnBadRequestWithoutScheduling_When_NoScanData() throws Exception {
        when(selectionResolver.resolve(any())).thenReturn(ResolvedSelection.empty());

        mvc.perform(post(JOBS_URL).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isBadRequest());

        verify(judgeJobStore, never()).create(any());
        verifyNoInteractions(jobExecutor, jobRunner);
    }

    @Test
    void Should_AcceptJob_When_ScanFinishedWithFindings() throws Exception {
        when(checkpointRepository.findAllLatestCheckpoints()).thenReturn(List.of(checkpoint(ScanStatus.COMPLETED)));
        when(selectionResolver.resolve(any())).thenReturn(new ResolvedSelection("scan",
                List.of(EligibleFinding.builder().findingId("finding").build()), List.of(), 0));
        when(clock.instant()).thenReturn(Instant.EPOCH);

        mvc.perform(post(JOBS_URL).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobIds.length()").value(1));

        verify(judgeJobStore).create(any());
        verify(jobExecutor).execute(any());
    }

    private static ScanCheckpoint checkpoint(ScanStatus scanStatus) {
        return ScanCheckpoint.builder().scanId("scan").spaceKey("B").scanStatus(scanStatus).build();
    }
}
