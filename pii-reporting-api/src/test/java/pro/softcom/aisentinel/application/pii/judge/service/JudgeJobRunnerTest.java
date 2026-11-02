package pro.softcom.aisentinel.application.pii.judge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.application.pii.judge.port.out.JudgeJobStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.FindingRemediationStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.PublishRemediationEventPort;
import pro.softcom.aisentinel.application.pii.remediation.service.EligibleFinding;
import pro.softcom.aisentinel.application.pii.remediation.service.ScanEventFindingResolver;
import pro.softcom.aisentinel.application.pii.remediation.service.ScanEventFindingResolver.Resolution;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorException;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;
import pro.softcom.aisentinel.domain.pii.judge.FindingJudgeAnswer;
import pro.softcom.aisentinel.domain.pii.judge.FindingJudgeRequest;
import pro.softcom.aisentinel.domain.pii.judge.JudgeApplyMode;
import pro.softcom.aisentinel.domain.pii.judge.JudgeEndpoint;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJob;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJobStatus;
import pro.softcom.aisentinel.domain.pii.judge.JudgeVerdict;
import pro.softcom.aisentinel.domain.pii.remediation.FindingReference;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediation;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus;
import pro.softcom.aisentinel.domain.pii.remediation.SpaceFalsePositivesChanged;
import pro.softcom.aisentinel.domain.pii.reporting.AccessPurpose;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JudgeJobRunner")
class JudgeJobRunnerTest {

    private static final String SCAN_ID = "scan-1";
    private static final String SPACE_KEY = "SPACE";
    private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");

    @Mock
    private ScanResultQuery scanResultQuery;
    @Mock
    private ScanEventFindingResolver findingResolver;
    @Mock
    private FindingRemediationStore findingRemediationStore;
    @Mock
    private JudgeJobStore judgeJobStore;
    @Mock
    private PiiDetectorClient piiDetectorClient;
    @Mock
    private PiiDetectionConfigRepository detectionConfigRepository;
    @Mock
    private PublishRemediationEventPort publishRemediationEventPort;

    private JudgeJobRunner runner;
    private EligibleFinding iban;
    private EligibleFinding email;

    @BeforeEach
    void setUp() {
        runner = new JudgeJobRunner(scanResultQuery, findingResolver, findingRemediationStore, judgeJobStore,
                piiDetectorClient, detectionConfigRepository, publishRemediationEventPort,
                Clock.fixed(NOW, ZoneOffset.UTC), new JudgeEndpoint(null, null));
        iban = finding("IBAN", "CH93 0076 2011 6238 5295 7", "fp-iban");
        email = finding("EMAIL", "a@b.ch", "fp-email");
        lenient().when(detectionConfigRepository.findConfig()).thenReturn(config());
        lenient().when(scanResultQuery.listItemEventsDecryptedByScanIdAndSpaceKey(SCAN_ID, SPACE_KEY,
                AccessPurpose.LLM_JUDGE)).thenReturn(List.of());
        lenient().when(findingResolver.resolve(any())).thenReturn(new Resolution(List.of(iban, email), 0));
        lenient().when(findingRemediationStore.findByIds(anyCollection())).thenReturn(List.of());
        lenient().when(judgeJobStore.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Should_ProposeFalsePositive_When_JudgeRejectsAboveThreshold")
    void Should_ProposeFalsePositive_When_JudgeRejectsAboveThreshold() {
        when(piiDetectorClient.judgeFinding(any()))
                .thenReturn(answer("FALSE_POSITIVE", 0.93), answer("TRUE_POSITIVE", 0.97));

        runner.run(job(JudgeApplyMode.PROPOSE), SCAN_ID, Set.of(iban.findingId(), email.findingId()));

        List<FindingRemediation> rows = upsertedRows();
        JudgeJob finished = lastJobUpdate();
        assertSoftly(softly -> {
            softly.assertThat(rows).extracting(FindingRemediation::status)
                    .containsExactly(FindingRemediationStatus.PROPOSED_FALSE_POSITIVE, FindingRemediationStatus.PENDING);
            softly.assertThat(rows.getFirst().judgement().verdict()).isEqualTo(JudgeVerdict.FALSE_POSITIVE);
            softly.assertThat(rows.getFirst().judgement().confidence()).isEqualTo(0.93);
            softly.assertThat(rows.getLast().judgement().verdict()).isEqualTo(JudgeVerdict.TRUE_POSITIVE);
            softly.assertThat(finished.status()).isEqualTo(JudgeJobStatus.COMPLETED);
            softly.assertThat(finished.processed()).isEqualTo(2);
            softly.assertThat(finished.falsePositives()).isEqualTo(1);
        });
        verify(publishRemediationEventPort, never()).publishFalsePositivesChanged(any());
    }

    @Test
    @DisplayName("Should_KeepFindingAsUnsure_When_RejectionBelowThreshold")
    void Should_KeepFindingAsUnsure_When_RejectionBelowThreshold() {
        when(findingResolver.resolve(any())).thenReturn(new Resolution(List.of(iban), 0));
        when(piiDetectorClient.judgeFinding(any())).thenReturn(answer("FALSE_POSITIVE", 0.5));

        runner.run(job(JudgeApplyMode.PROPOSE), SCAN_ID, Set.of(iban.findingId()));

        FindingRemediation row = upsertedRows().getFirst();
        assertSoftly(softly -> {
            softly.assertThat(row.status()).isEqualTo(FindingRemediationStatus.PENDING);
            softly.assertThat(row.judgement().verdict()).isEqualTo(JudgeVerdict.UNSURE);
            softly.assertThat(lastJobUpdate().falsePositives()).isZero();
        });
    }

    @Test
    @DisplayName("Should_ReportFalsePositiveAndPublish_When_AutoApply")
    void Should_ReportFalsePositiveAndPublish_When_AutoApply() {
        when(findingResolver.resolve(any())).thenReturn(new Resolution(List.of(iban), 0));
        when(piiDetectorClient.judgeFinding(any())).thenReturn(answer("FALSE_POSITIVE", 0.99));

        runner.run(job(JudgeApplyMode.AUTO_APPLY), SCAN_ID, Set.of(iban.findingId()));

        assertThat(upsertedRows().getFirst().status()).isEqualTo(FindingRemediationStatus.FALSE_POSITIVE);
        verify(publishRemediationEventPort).publishFalsePositivesChanged(new SpaceFalsePositivesChanged(SCAN_ID, SPACE_KEY));
    }

    @Test
    @DisplayName("Should_CountErrorAndSkipFinding_When_JudgeCannotAnswer")
    void Should_CountErrorAndSkipFinding_When_JudgeCannotAnswer() {
        when(findingResolver.resolve(any())).thenReturn(new Resolution(List.of(iban), 0));
        when(piiDetectorClient.judgeFinding(any()))
                .thenReturn(new FindingJudgeAnswer("ERROR", 0.0, "", "ConnectError: refused"));

        runner.run(job(JudgeApplyMode.PROPOSE), SCAN_ID, Set.of(iban.findingId()));

        verify(findingRemediationStore, never()).upsertAll(any());
        JudgeJob finished = lastJobUpdate();
        assertSoftly(softly -> {
            softly.assertThat(finished.status()).isEqualTo(JudgeJobStatus.COMPLETED);
            softly.assertThat(finished.errors()).isEqualTo(1);
            softly.assertThat(finished.processed()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("Should_StopAfterCurrentFinding_When_InterruptionRequested")
    void Should_StopAfterCurrentFinding_When_InterruptionRequested() {
        JudgeJob job = job(JudgeApplyMode.PROPOSE);
        when(piiDetectorClient.judgeFinding(any())).thenReturn(answer("TRUE_POSITIVE", 0.9));
        when(judgeJobStore.findById(job.id()))
                .thenReturn(Optional.of(job.toBuilder().status(JudgeJobStatus.INTERRUPTED).build()));

        runner.run(job, SCAN_ID, Set.of(iban.findingId(), email.findingId()));

        verify(piiDetectorClient, times(1)).judgeFinding(any());
        assertThat(lastJobUpdate().status()).isEqualTo(JudgeJobStatus.INTERRUPTED);
        assertThat(lastJobUpdate().processed()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should_FailJob_When_DetectorServiceUnreachable")
    void Should_FailJob_When_DetectorServiceUnreachable() {
        when(piiDetectorClient.judgeFinding(any())).thenThrow(new PiiDetectorException("down", null));

        runner.run(job(JudgeApplyMode.PROPOSE), SCAN_ID, Set.of(iban.findingId()));

        assertThat(lastJobUpdate().status()).isEqualTo(JudgeJobStatus.FAILED);
    }

    @Test
    @DisplayName("Should_LeaveRowUntouched_When_FindingLeftPendingMeanwhile")
    void Should_LeaveRowUntouched_When_FindingLeftPendingMeanwhile() {
        when(findingResolver.resolve(any())).thenReturn(new Resolution(List.of(iban), 0));
        when(piiDetectorClient.judgeFinding(any())).thenReturn(answer("FALSE_POSITIVE", 0.99));
        when(findingRemediationStore.findByIds(anyCollection()))
                .thenReturn(List.of(row(iban, FindingRemediationStatus.REDACTED)));

        runner.run(job(JudgeApplyMode.PROPOSE), SCAN_ID, Set.of(iban.findingId()));

        verify(findingRemediationStore, never()).upsertAll(any());
        assertThat(lastJobUpdate().falsePositives()).isZero();
    }

    @Test
    @DisplayName("Should_SendBoundedContextAndConfiguredEndpoint_When_Judging")
    void Should_SendBoundedContextAndConfiguredEndpoint_When_Judging() {
        when(findingResolver.resolve(any())).thenReturn(new Resolution(List.of(iban), 0));
        when(piiDetectorClient.judgeFinding(any())).thenReturn(answer("TRUE_POSITIVE", 0.9));

        runner.run(job(JudgeApplyMode.PROPOSE), SCAN_ID, Set.of(iban.findingId()));

        ArgumentCaptor<FindingJudgeRequest> captor = ArgumentCaptor.forClass(FindingJudgeRequest.class);
        verify(piiDetectorClient).judgeFinding(captor.capture());
        FindingJudgeRequest request = captor.getValue();
        assertSoftly(softly -> {
            softly.assertThat(request.piiType()).isEqualTo("IBAN");
            softly.assertThat(request.value()).isEqualTo(iban.sensitiveValue());
            softly.assertThat(request.context()).contains(iban.sensitiveValue());
            softly.assertThat(request.model()).isEqualTo("judge@q8");
            softly.assertThat(request.lmStudioHost()).isEqualTo("localhost");
            softly.assertThat(request.lmStudioPort()).isEqualTo(1234);
        });
    }

    @Test
    void Should_SendJudgeEndpointAndRequestedModel_When_StrataConfigured() {
        runner = new JudgeJobRunner(scanResultQuery, findingResolver, findingRemediationStore, judgeJobStore,
                piiDetectorClient, detectionConfigRepository, publishRemediationEventPort,
                Clock.fixed(NOW, ZoneOffset.UTC), new JudgeEndpoint("strata", 8081));
        when(findingResolver.resolve(any())).thenReturn(new Resolution(List.of(iban), 0));
        when(piiDetectorClient.judgeFinding(any())).thenReturn(answer("TRUE_POSITIVE", 0.99));

        runner.run(job(JudgeApplyMode.PROPOSE), SCAN_ID, Set.of(iban.findingId()));

        ArgumentCaptor<FindingJudgeRequest> captor = ArgumentCaptor.forClass(FindingJudgeRequest.class);
        verify(piiDetectorClient).judgeFinding(captor.capture());
        assertThat(captor.getValue().lmStudioHost()).isEqualTo("strata");
        assertThat(captor.getValue().lmStudioPort()).isEqualTo(8081);
        assertThat(captor.getValue().model()).isEqualTo("judge@q8");
    }

    private List<FindingRemediation> upsertedRows() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<FindingRemediation>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(findingRemediationStore, atLeastOnce()).upsertAll(captor.capture());
        return captor.getAllValues().stream().flatMap(Collection::stream).toList();
    }

    private JudgeJob lastJobUpdate() {
        ArgumentCaptor<JudgeJob> captor = ArgumentCaptor.forClass(JudgeJob.class);
        verify(judgeJobStore, atLeastOnce()).update(captor.capture());
        return captor.getValue();
    }

    private static JudgeJob job(JudgeApplyMode applyMode) {
        return JudgeJob.builder()
                .id("job-1").spaceKey(SPACE_KEY).status(JudgeJobStatus.RUNNING).model("judge@q8")
                .applyMode(applyMode).confidenceThreshold(0.8).total(2).actor("tester")
                .createdAt(NOW).updatedAt(NOW).build();
    }

    private static FindingJudgeAnswer answer(String verdict, double confidence) {
        return new FindingJudgeAnswer(verdict, confidence, "motif", "");
    }

    private static EligibleFinding finding(String piiType, String value, String fingerprint) {
        FindingReference reference = FindingReference.builder()
                .spaceKey(SPACE_KEY).pageId("p1").detector("MINISTRAL").piiType(piiType)
                .severity(PersonallyIdentifiableInformationSeverity.HIGH).valueFingerprint(fingerprint).build();
        return EligibleFinding.builder()
                .findingId(reference.findingId()).reference(reference).confidence(1.0).piiTypeLabel(piiType)
                .sensitiveValue(value).sensitiveContext("Valeur : " + value + " (fin)").pageTitle("Page")
                .occurrenceCount(1).build();
    }

    private static FindingRemediation row(EligibleFinding finding, FindingRemediationStatus status) {
        FindingReference reference = finding.reference();
        return FindingRemediation.builder()
                .findingId(finding.findingId()).scanId(SCAN_ID).spaceKey(SPACE_KEY).pageId(reference.pageId())
                .piiType(reference.piiType()).severity(reference.severity()).detector(reference.detector())
                .status(status).actor("someone").occurredAt(NOW).build();
    }

    private static PiiDetectionConfig config() {
        return new PiiDetectionConfig(1, true, true, true, 2048, 410, BigDecimal.valueOf(0.75), true,
                "localhost", 1234, "ministral-3b-pii-preview@q8_0", null, 4, false, null,
                LocalDateTime.of(2026, 9, 3, 10, 0), "tester");
    }
}
