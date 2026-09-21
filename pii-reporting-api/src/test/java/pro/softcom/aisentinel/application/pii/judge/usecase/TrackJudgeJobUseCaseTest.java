package pro.softcom.aisentinel.application.pii.judge.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.judge.port.in.JudgeSpaceSummary;
import pro.softcom.aisentinel.application.pii.judge.port.out.JudgeJobStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.FindingRemediationStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.RemediationConfigPort;
import pro.softcom.aisentinel.domain.pii.judge.JudgeApplyMode;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJob;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJobNotFoundException;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJobStatus;
import pro.softcom.aisentinel.domain.pii.judge.SpaceJudgementCounts;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackJudgeJobUseCase")
class TrackJudgeJobUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");

    @Mock
    private RemediationConfigPort remediationConfigPort;
    @Mock
    private JudgeJobStore judgeJobStore;
    @Mock
    private FindingRemediationStore findingRemediationStore;

    private TrackJudgeJobUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TrackJudgeJobUseCase(remediationConfigPort, judgeJobStore, findingRemediationStore,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(remediationConfigPort.isRemediationEnabled()).thenReturn(true);
    }

    @Test
    @DisplayName("Should_MarkInterrupted_When_CancellingRunningJob")
    void Should_MarkInterrupted_When_CancellingRunningJob() {
        when(judgeJobStore.findById("job")).thenReturn(Optional.of(job("A", JudgeJobStatus.RUNNING)));

        useCase.cancel("job");

        verify(judgeJobStore).update(argThat(job -> job.status() == JudgeJobStatus.INTERRUPTED
                && job.updatedAt().equals(NOW)));
    }

    @Test
    @DisplayName("Should_LeaveJobUntouched_When_CancellingFinishedJob")
    void Should_LeaveJobUntouched_When_CancellingFinishedJob() {
        when(judgeJobStore.findById("job")).thenReturn(Optional.of(job("A", JudgeJobStatus.COMPLETED)));

        useCase.cancel("job");

        verify(judgeJobStore, never()).update(any());
    }

    @Test
    @DisplayName("Should_ThrowNotFound_When_CancellingUnknownJob")
    void Should_ThrowNotFound_When_CancellingUnknownJob() {
        when(judgeJobStore.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.cancel("nope")).isInstanceOf(JudgeJobNotFoundException.class);
    }

    @Test
    @DisplayName("Should_MergeCountsAndActiveJobs_When_SummarisingSpaces")
    void Should_MergeCountsAndActiveJobs_When_SummarisingSpaces() {
        when(findingRemediationStore.countJudgementsBySpace())
                .thenReturn(List.of(new SpaceJudgementCounts("A", 10, 3)));
        when(judgeJobStore.findAllActive()).thenReturn(List.of(job("B", JudgeJobStatus.RUNNING)));

        List<JudgeSpaceSummary> summaries = useCase.spaceSummaries();

        assertThat(summaries).containsExactly(
                new JudgeSpaceSummary("A", 10, 3, null),
                new JudgeSpaceSummary("B", 0, 0, job("B", JudgeJobStatus.RUNNING)));
    }

    private static JudgeJob job(String spaceKey, JudgeJobStatus status) {
        return JudgeJob.builder().id("job").spaceKey(spaceKey).status(status).applyMode(JudgeApplyMode.PROPOSE)
                .confidenceThreshold(0.8).actor("tester").createdAt(NOW).updatedAt(NOW).build();
    }
}
