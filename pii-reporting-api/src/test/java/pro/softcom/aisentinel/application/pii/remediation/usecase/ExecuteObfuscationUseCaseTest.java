package pro.softcom.aisentinel.application.pii.remediation.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.remediation.port.in.ExecuteObfuscationPort.ObfuscationSubmission;
import pro.softcom.aisentinel.application.pii.remediation.port.out.ObfuscationJobStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.RemediationConfigPort;
import pro.softcom.aisentinel.application.pii.remediation.service.EligibleFinding;
import pro.softcom.aisentinel.application.pii.remediation.service.ObfuscationJobRunner;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver.ResolvedSelection;
import pro.softcom.aisentinel.domain.pii.remediation.AttachmentRedactionUnsupportedException;
import pro.softcom.aisentinel.domain.pii.remediation.FindingReference;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationJob;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationJobAlreadyRunningException;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationJobStatus;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationPlan;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationDisabledException;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationSelection;
import pro.softcom.aisentinel.domain.pii.remediation.SelectionOutdatedException;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecuteObfuscationUseCase")
class ExecuteObfuscationUseCaseTest {

    private static final String SPACE_KEY = "SPACE";
    private static final Instant NOW = Instant.parse("2026-07-06T10:00:00Z");

    @Mock
    private RemediationConfigPort remediationConfigPort;

    @Mock
    private SelectionResolver selectionResolver;

    @Mock
    private ObfuscationJobStore obfuscationJobStore;

    @Mock
    private ObfuscationJobRunner jobRunner;

    private ExecuteObfuscationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ExecuteObfuscationUseCase(remediationConfigPort, selectionResolver,
                obfuscationJobStore, jobRunner, Runnable::run, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(remediationConfigPort.isRemediationEnabled()).thenReturn(true);
        lenient().when(obfuscationJobStore.findActiveBySpace(SPACE_KEY)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Should_ThrowRemediationDisabled_When_FeatureFlagIsOff")
    void Should_ThrowRemediationDisabled_When_FeatureFlagIsOff() {
        when(remediationConfigPort.isRemediationEnabled()).thenReturn(false);
        ObfuscationSubmission submission = submissionWithChecksum("any");

        assertThatThrownBy(() -> useCase.execute(submission))
                .isInstanceOf(RemediationDisabledException.class);
    }

    @Test
    @DisplayName("Should_RefuseExecution_When_ScopeTargetsAnAttachment")
    void Should_RefuseExecution_When_ScopeTargetsAnAttachment() {
        RemediationSelection selection = RemediationSelection.builder()
                .spaceKey(SPACE_KEY)
                .pageId("p1")
                .attachmentName("report.xlsx")
                .build();
        ObfuscationSubmission submission = new ObfuscationSubmission(selection, "any", "actor");

        assertThatThrownBy(() -> useCase.execute(submission))
                .isInstanceOf(AttachmentRedactionUnsupportedException.class);
    }

    @Test
    @DisplayName("Should_ThrowSelectionOutdated_When_ChecksumDiverged")
    void Should_ThrowSelectionOutdated_When_ChecksumDiverged() {
        stubResolution(List.of(finding("EMAIL", "fp-1")));
        ObfuscationSubmission submission = submissionWithChecksum("stale-checksum");

        assertThatThrownBy(() -> useCase.execute(submission))
                .isInstanceOf(SelectionOutdatedException.class);
        verify(obfuscationJobStore, never()).create(any());
    }

    @Test
    @DisplayName("Should_RejectSubmission_When_SelectionResolvesToNothing")
    void Should_RejectSubmission_When_SelectionResolvesToNothing() {
        stubResolution(List.of());
        ObfuscationSubmission submission = submissionWithChecksum(ObfuscationPlan.checksumOf(List.of()));

        assertThatThrownBy(() -> useCase.execute(submission))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should_ThrowJobAlreadyRunning_When_SpaceHasActiveJob")
    void Should_ThrowJobAlreadyRunning_When_SpaceHasActiveJob() {
        List<EligibleFinding> findings = List.of(finding("EMAIL", "fp-1"));
        stubResolution(findings);
        when(obfuscationJobStore.findActiveBySpace(SPACE_KEY))
                .thenReturn(Optional.of(runningJob()));
        ObfuscationSubmission submission = submissionWithChecksum(checksumOf(findings));

        assertThatThrownBy(() -> useCase.execute(submission))
                .isInstanceOf(ObfuscationJobAlreadyRunningException.class);
        verify(obfuscationJobStore, never()).create(any());
    }

    @Test
    @DisplayName("Should_CreateFrozenJobAndRunAsync_When_SubmissionIsValid")
    void Should_CreateFrozenJobAndRunAsync_When_SubmissionIsValid() {
        List<EligibleFinding> findings = List.of(finding("EMAIL", "fp-1"), finding("IBAN", "fp-2"));
        ResolvedSelection resolved = stubResolution(findings);

        String jobId = useCase.execute(new ObfuscationSubmission(selection(), checksumOf(findings), "officer"));

        ArgumentCaptor<ObfuscationJob> captor = ArgumentCaptor.forClass(ObfuscationJob.class);
        verify(obfuscationJobStore).create(captor.capture());
        ObfuscationJob job = captor.getValue();
        verify(jobRunner).run(job, resolved);
        assertSoftly(softly -> {
            softly.assertThat(jobId).isEqualTo(job.id());
            softly.assertThat(job.spaceKey()).isEqualTo(SPACE_KEY);
            softly.assertThat(job.status()).isEqualTo(ObfuscationJobStatus.RUNNING);
            softly.assertThat(job.submittedSelection()).isEqualTo(selection());
            softly.assertThat(job.resolvedFindingIds())
                    .containsExactlyElementsOf(findings.stream().map(EligibleFinding::findingId).toList());
            softly.assertThat(job.processed()).isZero();
            softly.assertThat(job.total()).isEqualTo(2);
            softly.assertThat(job.outcomes()).isEmpty();
            softly.assertThat(job.actor()).isEqualTo("officer");
            softly.assertThat(job.createdAt()).isEqualTo(NOW);
            softly.assertThat(job.updatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    @DisplayName("Should_NotRunJob_When_StoreRejectsConcurrentCreation")
    void Should_NotRunJob_When_StoreRejectsConcurrentCreation() {
        List<EligibleFinding> findings = List.of(finding("EMAIL", "fp-1"));
        stubResolution(findings);
        doThrow(new ObfuscationJobAlreadyRunningException(SPACE_KEY))
                .when(obfuscationJobStore).create(any());
        ObfuscationSubmission submission = submissionWithChecksum(checksumOf(findings));

        assertThatThrownBy(() -> useCase.execute(submission))
                .isInstanceOf(ObfuscationJobAlreadyRunningException.class);
        verify(jobRunner, never()).run(any(), any());
    }

    private ResolvedSelection stubResolution(List<EligibleFinding> findings) {
        ResolvedSelection resolved = new ResolvedSelection("scan-1", findings, List.of(), 0);
        when(selectionResolver.resolve(selection())).thenReturn(resolved);
        return resolved;
    }

    private static String checksumOf(List<EligibleFinding> findings) {
        return ObfuscationPlan.checksumOf(findings.stream().map(EligibleFinding::findingId).toList());
    }

    private static ObfuscationSubmission submissionWithChecksum(String checksum) {
        return new ObfuscationSubmission(selection(), checksum, "officer");
    }

    private static RemediationSelection selection() {
        return RemediationSelection.builder()
                .spaceKey(SPACE_KEY)
                .piiTypes(List.of("EMAIL", "IBAN"))
                .build();
    }

    private static ObfuscationJob runningJob() {
        return ObfuscationJob.builder()
                .id("job-1")
                .spaceKey(SPACE_KEY)
                .status(ObfuscationJobStatus.RUNNING)
                .submittedSelection(selection())
                .actor("someone")
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private static EligibleFinding finding(String piiType, String fingerprint) {
        FindingReference reference = FindingReference.builder()
                .spaceKey(SPACE_KEY)
                .pageId("p1")
                .detector("PRESIDIO")
                .piiType(piiType)
                .severity(PersonallyIdentifiableInformationSeverity.MEDIUM)
                .valueFingerprint(fingerprint)
                .build();
        return EligibleFinding.builder()
                .findingId(reference.findingId())
                .reference(reference)
                .confidence(0.9)
                .piiTypeLabel(piiType)
                .maskedContext("masked")
                .pageTitle("Page")
                .build();
    }
}
