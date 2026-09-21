package pro.softcom.aisentinel.application.pii.judge.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.application.pii.judge.port.in.JudgeLaunchCommand;
import pro.softcom.aisentinel.application.pii.judge.port.in.JudgePlan;
import pro.softcom.aisentinel.application.pii.judge.port.in.JudgeScope;
import pro.softcom.aisentinel.application.pii.judge.port.out.JudgeJobStore;
import pro.softcom.aisentinel.application.pii.judge.service.JudgeJobRunner;
import pro.softcom.aisentinel.application.pii.remediation.port.out.RemediationConfigPort;
import pro.softcom.aisentinel.application.pii.remediation.port.out.FindingRemediationStore;
import pro.softcom.aisentinel.application.pii.remediation.service.ScanEventFindingResolver;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionEvaluator;
import pro.softcom.aisentinel.application.pii.reporting.SeverityCalculationService;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;
import pro.softcom.aisentinel.application.pii.remediation.service.EligibleFinding;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver.ResolvedSelection;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PersonallyIdentifiableInformationScanExecutionOrchestratorPort;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.aisentinel.domain.pii.reporting.LastScanMeta;
import pro.softcom.aisentinel.domain.pii.judge.JudgeScanInProgressException;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;
import pro.softcom.aisentinel.domain.pii.judge.JudgeApplyMode;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJob;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJobAlreadyRunningException;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJobStatus;
import pro.softcom.aisentinel.domain.pii.remediation.FindingReference;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationDisabledException;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationSelection;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("JudgeEvaluationUseCase")
class JudgeEvaluationUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");

    @Mock
    private RemediationConfigPort remediationConfigPort;
    @Mock
    private SelectionResolver selectionResolver;
    @Mock
    private JudgeJobStore judgeJobStore;
    @Mock
    private PiiDetectionConfigRepository detectionConfigRepository;
    @Mock
    private JudgeJobRunner jobRunner;

    @Mock
    private ScanCheckpointRepository checkpointRepository;
    @Mock
    private ScanResultQuery scanResultQuery;

    @Mock
    private PersonallyIdentifiableInformationScanExecutionOrchestratorPort scanExecutionOrchestrator;

    private JudgeEvaluationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new JudgeEvaluationUseCase(remediationConfigPort, selectionResolver, judgeJobStore,
                detectionConfigRepository, jobRunner, Runnable::run, checkpointRepository, scanResultQuery, scanExecutionOrchestrator, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(remediationConfigPort.isRemediationEnabled()).thenReturn(true);
        lenient().when(judgeJobStore.findActiveBySpace(anyString())).thenReturn(Optional.empty());
        lenient().when(detectionConfigRepository.findConfig()).thenReturn(config());
    }

    @Test
    void Should_PlanAndJudgeEarlierSpaceScan_When_AnotherSpaceWasScannedLater() {
        SeverityCalculationService severityService = mock(SeverityCalculationService.class);
        when(severityService.calculateSeverity("IBAN")).thenReturn(PersonallyIdentifiableInformationSeverity.HIGH);
        SelectionResolver resolver = new SelectionResolver(scanResultQuery, checkpointRepository,
                mock(FindingRemediationStore.class), new ScanEventFindingResolver(severityService), new SelectionEvaluator());
        useCase = new JudgeEvaluationUseCase(remediationConfigPort, resolver, judgeJobStore,
                detectionConfigRepository, jobRunner, Runnable::run, checkpointRepository, scanResultQuery,
                scanExecutionOrchestrator, Clock.fixed(NOW, ZoneOffset.UTC));
        when(scanResultQuery.findLatestScan()).thenReturn(Optional.of(new LastScanMeta("scan-other", NOW, 1)));
        when(checkpointRepository.findLatestBySpace("PCYBER"))
                .thenReturn(Optional.of(ScanCheckpoint.builder().scanId("scan-cyber").spaceKey("PCYBER").build()));
        when(scanResultQuery.listItemEventsEncryptedByScanIdAndSpaceKey("scan-cyber", "PCYBER"))
                .thenReturn(List.of(ConfluenceContentScanResult.builder().scanId("scan-cyber")
                        .spaceKey("PCYBER").pageId("p1").detectedPIIs(List.of(
                                DetectedPersonallyIdentifiableInformation.builder().piiType("IBAN")
                                        .valueFingerprint("fp-iban").build())).build()));
        JudgeScope scope = new JudgeScope(List.of("PCYBER"), null);

        JudgePlan plan = useCase.plan(scope);
        useCase.launch(command(scope, "judge"));

        assertSoftly(softly -> {
            softly.assertThat(plan.totalFindings()).isEqualTo(1);
            softly.assertThat(plan.findingsBySpace()).containsEntry("PCYBER", 1);
        });
        verify(jobRunner).run(argThat(job -> "PCYBER".equals(job.spaceKey())), eq("scan-cyber"),
                argThat(ids -> ids.size() == 1));
    }

    @Test
    @DisplayName("Should_CountPendingFindingsPerSpace_When_Planning")
    void Should_CountPendingFindingsPerSpace_When_Planning() {
        when(selectionResolver.resolve(argThat(selectionOf("A")))).thenReturn(resolved(2, 1));
        when(selectionResolver.resolve(argThat(selectionOf("B")))).thenReturn(resolved(0, 0));

        JudgePlan plan = useCase.plan(new JudgeScope(List.of("A", "B"), null));

        assertSoftly(softly -> {
            softly.assertThat(plan.totalFindings()).isEqualTo(3);
            softly.assertThat(plan.findingsBySpace()).containsExactlyInAnyOrderEntriesOf(Map.of("A", 3, "B", 0));
        });
    }

    @Test
    @DisplayName("Should_CreateOneJobPerSpaceWithFindingsAndRunThem_When_Launched")
    void Should_CreateOneJobPerSpaceWithFindingsAndRunThem_When_Launched() {
        when(selectionResolver.resolve(argThat(selectionOf("A")))).thenReturn(resolved(2, 0));
        when(selectionResolver.resolve(argThat(selectionOf("B")))).thenReturn(resolved(0, 0));
        when(selectionResolver.resolve(argThat(selectionOf("C")))).thenReturn(resolved(1, 1));

        List<String> jobIds = useCase.launch(command(new JudgeScope(List.of("A", "B", "C"), null), "judge@q4"));

        ArgumentCaptor<JudgeJob> created = ArgumentCaptor.forClass(JudgeJob.class);
        verify(judgeJobStore, times(2)).create(created.capture());
        verify(jobRunner).run(argThat(job -> job.spaceKey().equals("A")), eq("scan-1"), argThat(ids -> ids.size() == 2));
        verify(jobRunner).run(argThat(job -> job.spaceKey().equals("C")), eq("scan-1"), argThat(ids -> ids.size() == 2));
        assertSoftly(softly -> {
            softly.assertThat(jobIds).hasSize(2);
            softly.assertThat(created.getAllValues()).extracting(JudgeJob::spaceKey).containsExactly("A", "C");
            softly.assertThat(created.getAllValues()).allSatisfy(job -> {
                softly.assertThat(job.status()).isEqualTo(JudgeJobStatus.RUNNING);
                softly.assertThat(job.model()).isEqualTo("judge@q4");
                softly.assertThat(job.applyMode()).isEqualTo(JudgeApplyMode.PROPOSE);
                softly.assertThat(job.confidenceThreshold()).isEqualTo(0.8);
                softly.assertThat(job.total()).isEqualTo(2);
            });
        });
    }

    @ParameterizedTest
    @EnumSource(value = ScanStatus.class, names = {"RUNNING", "NOT_STARTED"})
    void Should_RejectLaunchWithoutCreatingJobs_When_CurrentScanIsUnfinished(ScanStatus status) {
        when(checkpointRepository.findAllLatestCheckpoints()).thenReturn(List.of(checkpoint("current", status)));
        lenient().when(scanResultQuery.findLatestScan()).thenReturn(Optional.of(new LastScanMeta("current", NOW, 1)));
        JudgeLaunchCommand command = command(new JudgeScope(List.of("A"), null), "judge");

        assertThatThrownBy(() -> useCase.launch(command)).isInstanceOf(JudgeScanInProgressException.class);

        verifyNoInteractions(selectionResolver, judgeJobStore, jobRunner, detectionConfigRepository);
    }

    @ParameterizedTest
    @EnumSource(value = ScanStatus.class, names = {"COMPLETED", "FAILED", "INTERRUPTED"})
    void Should_Launch_When_CurrentScanIsTerminal(ScanStatus status) {
        when(checkpointRepository.findAllLatestCheckpoints()).thenReturn(List.of(checkpoint("current", status)));
        when(selectionResolver.resolve(any())).thenReturn(resolved(1, 0));

        useCase.launch(command(new JudgeScope(List.of("A"), null), "judge"));

        verify(judgeJobStore).create(any());
        verify(jobRunner).run(any(), eq("scan-1"), any());
    }

    @ParameterizedTest
    @EnumSource(value = ScanStatus.class, names = {"RUNNING", "NOT_STARTED", "PAUSED"})
    void Should_Launch_When_OnlyAnOlderScanIsUnfinished(ScanStatus status) {
        when(checkpointRepository.findAllLatestCheckpoints()).thenReturn(List.of(
                checkpoint("old", status), checkpoint("current", ScanStatus.COMPLETED)));
        when(scanResultQuery.findLatestScan()).thenReturn(Optional.of(new LastScanMeta("current", NOW, 1)));
        when(selectionResolver.resolve(any())).thenReturn(resolved(1, 0));

        useCase.launch(command(new JudgeScope(List.of("A"), null), "judge"));

        verify(judgeJobStore).create(any());
    }

    @Test
    void Should_RejectLaunch_When_ScanStartsBeforeAnyEventExists() {
        when(checkpointRepository.findAllLatestCheckpoints()).thenReturn(List.of(checkpoint("new", ScanStatus.NOT_STARTED)));
        JudgeLaunchCommand command = command(new JudgeScope(List.of("A"), null), "judge");

        assertThatThrownBy(() -> useCase.launch(command)).isInstanceOf(JudgeScanInProgressException.class);

        verifyNoInteractions(selectionResolver, judgeJobStore, jobRunner);
    }

    @Test
    void Should_Launch_When_CurrentScanIsPaused() {
        when(checkpointRepository.findAllLatestCheckpoints()).thenReturn(List.of(checkpoint("new", ScanStatus.PAUSED)));
        when(selectionResolver.resolve(any())).thenReturn(resolved(1, 0));

        useCase.launch(command(new JudgeScope(List.of("A"), null), "judge"));

        verify(judgeJobStore).create(any());
    }

    @Test
    void Should_Launch_When_PausedScanStillHasQueuedSpaces() {
        when(checkpointRepository.findAllLatestCheckpoints()).thenReturn(List.of(
                checkpoint("new", ScanStatus.PAUSED), checkpoint("new", ScanStatus.NOT_STARTED)));
        when(selectionResolver.resolve(any())).thenReturn(resolved(1, 0));

        useCase.launch(command(new JudgeScope(List.of("A"), null), "judge"));

        verify(judgeJobStore).create(any());
    }

    @Test
    void Should_RejectLaunch_When_AnyScanIsActivelyExecutingBeforeCheckpointsExist() {
        when(scanExecutionOrchestrator.hasActiveScan()).thenReturn(true);
        JudgeLaunchCommand command = command(new JudgeScope(List.of("A"), null), "judge");

        assertThatThrownBy(() -> useCase.launch(command)).isInstanceOf(JudgeScanInProgressException.class);

        verifyNoInteractions(checkpointRepository, scanResultQuery, selectionResolver, judgeJobStore, jobRunner);
    }

    private static ScanCheckpoint checkpoint(String scanId, ScanStatus status) {
        return ScanCheckpoint.builder().scanId(scanId).spaceKey("other-space").scanStatus(status).build();
    }

    @Test
    @DisplayName("Should_FallBackToConfiguredModel_When_NoModelRequested")
    void Should_FallBackToConfiguredModel_When_NoModelRequested() {
        when(selectionResolver.resolve(any())).thenReturn(resolved(1, 0));

        useCase.launch(command(new JudgeScope(List.of("A"), null), null));

        verify(judgeJobStore).create(argThat(job -> "ministral-3b-pii-preview@q8_0".equals(job.model())));
    }

    @Test
    @DisplayName("Should_UseConfiguredJudgeModel_When_NoModelRequested")
    void Should_UseConfiguredJudgeModel_When_NoModelRequested() {
        when(detectionConfigRepository.findConfig()).thenReturn(config("google/gemma-4-31b"));
        when(selectionResolver.resolve(any())).thenReturn(resolved(1, 0));

        useCase.launch(command(new JudgeScope(List.of("A"), null), null));

        verify(judgeJobStore).create(argThat(job -> "google/gemma-4-31b".equals(job.model())));
    }

    @Test
    @DisplayName("Should_FallBackToDetectionModel_When_ConfiguredJudgeModelIsBlank")
    void Should_FallBackToDetectionModel_When_ConfiguredJudgeModelIsBlank() {
        when(detectionConfigRepository.findConfig()).thenReturn(config("   "));
        when(selectionResolver.resolve(any())).thenReturn(resolved(1, 0));

        useCase.launch(command(new JudgeScope(List.of("A"), null), null));

        verify(judgeJobStore).create(argThat(job -> "ministral-3b-pii-preview@q8_0".equals(job.model())));
    }

    @Test
    @DisplayName("Should_KeepRequestedModel_When_JudgeModelIsAlsoConfigured")
    void Should_KeepRequestedModel_When_JudgeModelIsAlsoConfigured() {
        when(selectionResolver.resolve(any())).thenReturn(resolved(1, 0));

        useCase.launch(command(new JudgeScope(List.of("A"), null), "openai/gpt-oss-120b"));

        verify(judgeJobStore).create(argThat(job -> "openai/gpt-oss-120b".equals(job.model())));
    }

    @Test
    @DisplayName("Should_RejectLaunch_When_ScopeResolvesToNothing")
    void Should_RejectLaunch_When_ScopeResolvesToNothing() {
        when(selectionResolver.resolve(any())).thenReturn(resolved(0, 0));
        JudgeLaunchCommand command = command(new JudgeScope(List.of("A"), null), null);

        assertThatThrownBy(() -> useCase.launch(command)).isInstanceOf(IllegalArgumentException.class);
        verify(judgeJobStore, never()).create(any());
        verifyNoInteractions(jobRunner);
    }

    @Test
    @DisplayName("Should_RejectLaunch_When_SpaceAlreadyHasRunningJob")
    void Should_RejectLaunch_When_SpaceAlreadyHasRunningJob() {
        when(judgeJobStore.findActiveBySpace("A")).thenReturn(Optional.of(runningJob("A")));
        JudgeLaunchCommand command = command(new JudgeScope(List.of("A"), null), null);

        assertThatThrownBy(() -> useCase.launch(command)).isInstanceOf(JudgeJobAlreadyRunningException.class);
        verify(judgeJobStore, never()).create(any());
    }

    @Test
    @DisplayName("Should_ThrowRemediationDisabled_When_FeatureFlagIsOff")
    void Should_ThrowRemediationDisabled_When_FeatureFlagIsOff() {
        when(remediationConfigPort.isRemediationEnabled()).thenReturn(false);
        JudgeScope scope = new JudgeScope(List.of("A"), null);

        assertThatThrownBy(() -> useCase.plan(scope)).isInstanceOf(RemediationDisabledException.class);
    }

    private static JudgeLaunchCommand command(JudgeScope scope, String model) {
        return new JudgeLaunchCommand(scope, model, JudgeApplyMode.PROPOSE, 0.8, "tester");
    }

    private static org.mockito.ArgumentMatcher<RemediationSelection> selectionOf(String spaceKey) {
        return selection -> selection != null && spaceKey.equals(selection.spaceKey());
    }

    private static ResolvedSelection resolved(int pageFindings, int attachmentFindings) {
        return new ResolvedSelection("scan-1", findings(pageFindings, null), findings(attachmentFindings, "doc.pdf"), 0);
    }

    private static List<EligibleFinding> findings(int count, String attachmentName) {
        return java.util.stream.IntStream.range(0, count).mapToObj(index -> {
            FindingReference reference = FindingReference.builder()
                    .spaceKey("S").pageId("p1").attachmentName(attachmentName).detector("MINISTRAL").piiType("IBAN")
                    .severity(PersonallyIdentifiableInformationSeverity.HIGH)
                    .valueFingerprint("fp-" + attachmentName + index).build();
            return EligibleFinding.builder().findingId(reference.findingId()).reference(reference).build();
        }).toList();
    }

    private static JudgeJob runningJob(String spaceKey) {
        return JudgeJob.builder().id("job").spaceKey(spaceKey).status(JudgeJobStatus.RUNNING)
                .applyMode(JudgeApplyMode.PROPOSE).confidenceThreshold(0.8).actor("x")
                .createdAt(NOW).updatedAt(NOW).build();
    }

    private static PiiDetectionConfig config() {
        return config(null);
    }

    private static PiiDetectionConfig config(String lmStudioJudgeModel) {
        return new PiiDetectionConfig(1, true, true, true, 2048, 410, BigDecimal.valueOf(0.75), true,
                "localhost", 1234, "ministral-3b-pii-preview@q8_0", lmStudioJudgeModel, 4, false, null,
                LocalDateTime.of(2026, 9, 3, 10, 0), "tester");
    }
}
